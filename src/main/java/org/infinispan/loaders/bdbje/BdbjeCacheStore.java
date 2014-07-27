package org.infinispan.loaders.bdbje;

import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.JEVersion;
import com.sleepycat.util.ExceptionUnwrapper;

import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfiguration;
import org.infinispan.loaders.bdbje.logging.Log;
import org.infinispan.marshall.core.MarshalledEntry;
import org.infinispan.metadata.InternalMetadata;
import org.infinispan.persistence.TaskContextImpl;
import org.infinispan.persistence.spi.AdvancedLoadWriteStore;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.commons.util.Util;
import org.infinispan.executors.ExecutorAllCompletionService;
import org.infinispan.util.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * An Oracle SleepyCat JE implementation of a {@link org.infinispan.loaders.CacheStore}.  <p/>This implementation uses
 * two databases <ol> <li>stored entry database: <tt>/{location}/CacheInstance-{@link org.infinispan.Cache#getName()
 * name}</tt></li> {@link org.infinispan.container.entries.InternalCacheEntry stored entries} are stored here, keyed on
 * {@link org.infinispan.container.entries.InternalCacheEntry#getKey()} <li>class catalog database:
 * <tt>/{location}/CacheInstance-{@link org.infinispan.Cache#getName() name}_class_catalog</tt></li> class descriptions
 * are stored here for efficiency reasons. </ol> <p/> <p><tt>/{location}/je.properties</tt> is optional and will
 * override any parameters to the internal SleepyCat JE {@link EnvironmentConfig}.</p>
 * <p/>
 * All data access is transactional.  Any attempted reads to locked records will block.  The maximum duration of this is
 * set in nanoseconds via the parameter {@link org.infinispan.loaders.bdbje.BdbjeCacheStoreConfig#getLockAcquistionTimeout()}.
 * Calls to {@link org.infinispan.loaders.CacheStore#prepare(java.util.List, org.infinispan.transaction.xa.GlobalTransaction,
 * boolean)}  will attempt to resolve deadlocks, retrying up to {@link org.infinispan.loaders.bdbje.BdbjeCacheStoreConfig#getMaxTxRetries()}
 * attempts.
 * <p/>
 * Unlike the C version of SleepyCat, JE does not support MVCC or READ_COMMITTED isolation.  In other words, readers
 * will block on any data held by a pending transaction.  As such, it is best practice to keep the duration between
 * <code>prepare</code> and <code>commit</code> as short as possible.
 * <p/>
 *
 * @author Adrian Cole
 * @author Manik Surtani
 * @author Eugene Scripnik
 * @since 4.0
 */
public class BdbjeCacheStore implements AdvancedLoadWriteStore {

   private static final Log log = LogFactory.getLog(BdbjeCacheStore.class, Log.class);
   private static final boolean trace = log.isTraceEnabled();

   private Environment env;
   private StoredClassCatalog catalog;
   private Database cacheDb;
   private Database expiryDb;
   private StoredMap<byte[], byte[]> cacheMap;
   private StoredSortedMap<Long, byte[]> expiryMap;
   private BdbjeCacheStoreConfiguration configuration;
   private InitializationContext ctx;
   private BdbjeResourceFactory factory;

   @Override
   public void init(InitializationContext ctx) {
      this.ctx = ctx;
      this.configuration = ctx.getConfiguration();
      this.factory = new BdbjeResourceFactory(configuration);
      printLicense();
   }
   
   /**
    * {@inheritDoc} Validates configuration, configures and opens the {@link Environment}, then {@link
    * org.infinispan.loaders.bdbje.BdbjeCacheStore#openSleepyCatResources() opens the databases}.  When this is
    * finished, transactional and purging services are instantiated.
    */
   @Override
   public void start() {
      if (trace) log.trace("starting BdbjeCacheStore");

      openSleepyCatResources();
      log.debugf("started cache store %s", this);
   }

   /**
    * Opens the SleepyCat environment and all databases.  A {@link StoredMap} instance is provided which persists the
    * CacheStore.
    */
   private void openSleepyCatResources() {
      if (trace) log.tracef("creating je environment with home dir %s", configuration.location());
      String cacheName = ctx.getCache().getName();
      
      String cacheDbName = cacheName;
      if (configuration.cacheDbNamePrefix() != null) {
         cacheDbName = configuration.cacheDbNamePrefix() + "_" + cacheName;
	  }
      
      String expiryDbName = cacheName + "_expiry";
      if (configuration.expiryDbPrefix() != null) {
         expiryDbName = configuration.expiryDbPrefix() + "_expiry";
      }

      String catalogDbName = configuration.catalogDbName();
	  if (catalogDbName == null) {
		 catalogDbName = cacheDbName + "_class_catalog";
      }

      File location = verifyOrCreateEnvironmentDirectory(new File(configuration.location()));
      try {
         env = factory.createEnvironment(location, readEnvironmentProperties());
         cacheDb = factory.createDatabase(env, cacheDbName);
         Database catalogDb = factory.createDatabase(env, catalogDbName);
         expiryDb = factory.createDatabase(env, expiryDbName);
         catalog = factory.createStoredClassCatalog(catalogDb);
         cacheMap = factory.createStoredMapViewOfDatabase(cacheDb, catalog, ctx.getMarshaller());
         expiryMap = factory.createStoredSortedMapForKeyExpiry(expiryDb, catalog, ctx.getMarshaller());
      } catch (DatabaseException e) {
         throw convertToCacheLoaderException("could not open sleepycat je resource", e);
      }
   }

   private Properties readEnvironmentProperties() {
      String fileName = configuration.environmentPropertiesFile();
      if (fileName == null || fileName.trim().isEmpty()) return null;
      InputStream i = FileLookupFactory.newInstance().lookupFile(fileName, factory.getClass().getClassLoader());
      if (i != null) {
         Properties p = new Properties();
         try {
            p.load(i);
         } catch (IOException ioe) {
            throw new PersistenceException("Unable to read environment properties file " + fileName, ioe);
         } finally {
            Util.close(i);
         }
         return p;
      } else {
         return null;
      }
   }

   // not private so that this can be unit tested

   File verifyOrCreateEnvironmentDirectory(File location) {
      if (!location.exists()) {
         boolean created = location.mkdirs();
         if (!created) throw new PersistenceException("Unable to create cache loader location " + location);

      }
      if (!location.isDirectory()) {
         throw new PersistenceException("Cache loader location [" + location + "] is not a directory!");
      }
      return location;
   }

   /**
    * Stops transaction and purge processing and closes the SleepyCat environment.  The environment and databases are
    * not removed from the file system. Exceptions during close of databases are ignored as closing the environment
    * will ensure the databases are also.
    */
   @Override
   public void stop() {
      if (trace) log.trace("stopping BdbjeCacheStore");
      closeSleepyCatResources();
      log.debugf("started cache store %s", this);
   }

   private void closeSleepyCatResources() {
      cacheMap = null;
      expiryMap = null;
      closeDatabases();
      closeEnvironment();
   }

   /**
    * Exceptions are ignored so that {@link org.infinispan.loaders.bdbje.BdbjeCacheStore#closeEnvironment()}  will
    * execute.
    */
   private void closeDatabases() {
      if (trace) log.trace("closing databases");
      try {
         cacheDb.close();
      } catch (Exception e) {
         log.errorClosingDatabase(e);
      }
      try {
         expiryDb.close();
      } catch (Exception e) {
         log.errorClosingDatabase(e);
      }
      try {
         catalog.close();
      } catch (Exception e) {
         log.errorClosingCatalog(e);
      }
      catalog = null;
      cacheDb = null;
      expiryDb = null;
   }

   private void closeEnvironment() {
      if (env != null) {
         try {
            env.close();
         } catch (DatabaseException e) {
            throw new PersistenceException("Unexpected exception closing cacheStore", e);
         }
      }
      env = null;
   }

   @Override
   public void process(KeyFilter filter, CacheLoaderTask task, Executor executor, boolean fetchValue, boolean fetchMetadata) {
      int batchSize = 100;
      ExecutorAllCompletionService eacs = new ExecutorAllCompletionService(executor);
      TaskContextImpl taskContext = new TaskContextImpl();
      List<byte[]> keys = new ArrayList<byte[]>(batchSize);
      try {
         for (byte[] key : cacheMap.keySet()) {
            if (taskContext.isStopped()) {
               break;
            }
            keys.add(key);
            if (keys.size() == batchSize) {
               submitProcessTask(filter, task, eacs, taskContext, keys);
               keys = new ArrayList<byte[]>(batchSize);
            }
         }
         if (!keys.isEmpty() && !taskContext.isStopped()) {
             submitProcessTask(filter, task, eacs, taskContext, keys);
         }
         
         eacs.waitUntilAllCompleted();
         if (eacs.isExceptionThrown()) {
             throw new PersistenceException("Execution exception!", eacs.getFirstException());
         }
      } catch (Exception e) {
         throw convertToCacheLoaderException("error processing entries", e);
      }
   }

   private void submitProcessTask(final KeyFilter filter, final CacheLoaderTask task, ExecutorAllCompletionService eacs,
         final TaskContextImpl taskContext, final List<byte[]> keys) {
      eacs.submit(new Callable<Void>() {
         @Override
         public Void call() throws Exception {
            try {
               long now = ctx.getTimeService().wallClockTime();
               for (byte[] keyBytes : keys) {
                  if (taskContext.isStopped()) {
                      break;
                  }
                  Object key = unmarshall(keyBytes);
                  if (filter == null || filter.shouldLoadKey(key)) {
                     MarshalledEntry unmarshall = (MarshalledEntry) unmarshall(cacheMap.get(keyBytes));
                     InternalMetadata metadata = unmarshall.getMetadata();
                     if (metadata == null || !metadata.isExpired(now)) {
                         task.processEntry(unmarshall, taskContext);
                     }
                  }
               }
            } catch (Exception e) {
               log.errorExecutingParallelStoreTask(e);
               throw e;
            }
            return null;
         }
      });
   }

   private boolean isExpired(MarshalledEntry me, long now) {
      return me.getMetadata() != null && me.getMetadata().isExpired(now);
   }

   @Override
   public int size() {
      return cacheMap.size();
   }

   @Override
   public boolean contains(Object key) {
      try {
         return cacheMap.containsKey(key);
      } catch (RuntimeException caught) {
         throw convertToCacheLoaderException("error checking key " + key, caught);
      }
   }


   /**
    * {@inheritDoc} This implementation delegates to {@link StoredMap#remove(Object)}
    */
   @Override
   public boolean delete(Object key) {
      try {
         if (cacheMap.containsKey(key)) {
            return cacheMap.keySet().remove(key);
         }
         return false;
      } catch (RuntimeException caught) {
         throw convertToCacheLoaderException("error removing key " + key, caught);
      }
   }

   /**
    * {@inheritDoc} This implementation delegates to {@link StoredMap#get(Object)}.  If the object is expired, it will
    * not be returned.
    */
   @Override
   public MarshalledEntry load(Object key) {
      try {
         MarshalledEntry me = (MarshalledEntry) unmarshall(cacheMap.get(marshall(key)));
         if (me == null) return null;

         InternalMetadata meta = me.getMetadata();
         if (meta != null && meta.isExpired(ctx.getTimeService().wallClockTime())) {
            return null;
         }
         return me;
      } catch (Exception e) {
         throw convertToCacheLoaderException("error loading key " + key, e);
      }
   }

   /**
    * {@inheritDoc} This implementation delegates to {@link StoredMap#put(Object, Object)}
    */
   @Override
   public void write(MarshalledEntry entry) {
      try {
         cacheMap.put(marshall(entry.getKey()), marshall(entry));
         InternalMetadata meta = entry.getMetadata();
         if (meta != null && meta.expiryTime() > -1) {
            addNewExpiry(entry);
         }
      } catch (Exception e) {
         throw convertToCacheLoaderException("error storing entry " + entry, e);
      }
   }

   private void addNewExpiry(MarshalledEntry entry) throws IOException, InterruptedException {
      long expiry = entry.getMetadata().expiryTime();
      long maxIdle = entry.getMetadata().maxIdle();
      if (maxIdle > 0) {
         // Coding getExpiryTime() for transient entries has the risk of
         // being a moving target
         // which could lead to unexpected results, hence, InternalCacheEntry
         // calls are required
         expiry = maxIdle + System.currentTimeMillis();
      }
      expiryMap.put(expiry, marshall(entry.getKey()));
   }

   /**
    * {@inheritDoc} This implementation delegates to {@link StoredMap#clear()}
    */
   @Override
   public void clear() {
      try {
         cacheMap.clear();
         expiryMap.clear();
      } catch (RuntimeException caught) {
         throw convertToCacheLoaderException("error clearing store", caught);
      }
   }

   /**
    * In order to adhere to APIs which do not throw checked exceptions, BDBJE wraps IO and DatabaseExceptions inside
    * RuntimeExceptions.  These special Exceptions implement {@link com.sleepycat.util.ExceptionWrapper}.  This method
    * will look for any of that type of Exception and encapsulate it into a CacheLoaderException.  In doing so, the
    * real root cause can be obtained.
    *
    * @param message what to attach to the CacheLoaderException
    * @param caught  exception to parse
    * @return CacheLoaderException with the correct cause
    */
   PersistenceException convertToCacheLoaderException(String message, Exception caught) {
      caught = ExceptionUnwrapper.unwrap(caught);
      return (caught instanceof PersistenceException) ? (PersistenceException) caught :
            new PersistenceException(message, caught);
   }

   /**
    * Iterate through {@link com.sleepycat.collections.StoredMap#entrySet()} and remove, if expired.
    */
   @Override
   public void purge(Executor threadPool, PurgeListener listener) {
      try {
         Map<Long, byte[]> expired = expiryMap.headMap(ctx.getTimeService().wallClockTime(), true);
         for (Map.Entry<Long, byte[]> entry : expired.entrySet()) {
            expiryMap.remove(entry.getKey());

            byte[] keyBytes = entry.getValue();
            MarshalledEntry me = (MarshalledEntry) unmarshall(cacheMap.get(keyBytes));
            InternalMetadata metadata = me.getMetadata();
            // can happen if we update entry, two expiry pairs will exist in map
            // so we should always check real expiration time
            if (metadata != null && metadata.isExpired(ctx.getTimeService().wallClockTime())) {
                cacheMap.remove(keyBytes);
                listener.entryPurged(keyBytes);
            }
         }
      } catch (Exception e) {
         throw convertToCacheLoaderException("error purging expired entries", e);
      }
   }

   private byte[] marshall(Object entry) throws IOException, InterruptedException {
      return ctx.getMarshaller().objectToByteBuffer(entry);
   }

   private Object unmarshall(byte[] bytes) throws IOException, ClassNotFoundException {
      if (bytes == null)
         return null;

      return ctx.getMarshaller().objectFromByteBuffer(bytes);
   }

   /**
    * prints terms of use for Berkeley DB JE
    */
   public void printLicense() {
      String license = "\n*************************************************************************************\n" +
            "Berkeley DB Java Edition version: " + JEVersion.CURRENT_VERSION.toString() + "\n" +
            "Infinispan can use Berkeley DB Java Edition from Oracle \n" +
            "(http://www.oracle.com/database/berkeley-db/je/index.html)\n" +
            "for persistent, reliable and transaction-protected data storage.\n" +
            "If you choose to use Berkeley DB Java Edition with Infinispan, you must comply with the terms\n" +
            "of Oracle's public license, included in the file LICENSE.txt.\n" +
            "If you prefer not to release the source code for your own application in order to comply\n" +
            "with the Oracle public license, you may purchase a different license for use of\n" +
            "Berkeley DB Java Edition with Infinispan.\n" +
            "See http://www.oracle.com/database/berkeley-db/je/index.html for pricing and license terms\n" +
            "*************************************************************************************";
      System.out.println(license);
   }

}
