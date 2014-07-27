package org.infinispan.loaders.bdbje;

import com.sleepycat.bind.ByteArrayBinding;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.collections.CurrentTransaction;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.*;
import com.sleepycat.util.ExceptionUnwrapper;

import org.infinispan.commons.CacheException;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfiguration;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Factory that assembles objects specific to the SleepyCat JE API.
 *
 * @author Adrian Cole
 * @since 4.0
 */
public class BdbjeResourceFactory {
    private static final Log log = LogFactory.getLog(BdbjeResourceFactory.class);
    private static final boolean trace = log.isTraceEnabled();

    private BdbjeCacheStoreConfiguration config;

    public BdbjeResourceFactory(BdbjeCacheStoreConfiguration config) {
        this.config = config;
    }

    /**
     * @return PreparableTransactionRunner that will try to resolve deadlocks maximum of {@link
     *         BdbjeCacheStoreConfig#getMaxTxRetries()} times.
     */
    public PreparableTransactionRunner createPreparableTransactionRunner(Environment env) {
        return new PreparableTransactionRunner(env, config.maxTxRetries(), null);
    }

    public CurrentTransaction createCurrentTransaction(Environment env) {
        return CurrentTransaction.getInstance(env);
    }

    /**
     * Open the environment, creating it if it doesn't exist.
     *
     * @param envLocation base directory where the Environment will write files
     * @return open Environment with a lock timeout of {@link org.infinispan.loaders.bdbje.BdbjeCacheStoreConfig#getLockAcquistionTimeout()}
     *         milliseconds.
     */
    public Environment createEnvironment(File envLocation, Properties environmentProperties) throws DatabaseException {
        EnvironmentConfig envConfig = environmentProperties == null ? new EnvironmentConfig() : new EnvironmentConfig(environmentProperties);
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setLockTimeout(config.lockAcquisitionTimeout(), TimeUnit.MILLISECONDS);
        if (trace) log.tracef("opening or creating je environment at %s", envLocation);
        Environment env = new Environment(envLocation, envConfig);
        log.debugf("opened je environment at %s", envLocation);
        return env;
    }

    public StoredClassCatalog createStoredClassCatalog(Database catalogDb) throws DatabaseException {
        StoredClassCatalog catalog = new StoredClassCatalog(catalogDb);
        log.debugf("created stored class catalog from database %s", config.catalogDbName());
        return catalog;
    }

    /**
     * Open the database, creating it if it doesn't exist.
     *
     * @return open transactional Database
     */
    public Database createDatabase(Environment env, String name) throws DatabaseException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        if (trace) log.tracef("opening or creating database %s", name);
        Database db = env.openDatabase(null, name, dbConfig);
        log.debugf("opened database %s", name);
        return db;
    }

    /**
     * create a {@link com.sleepycat.collections.StoredMap} persisted by the <code>database</code>
     *
     * @param database     where entries in the StoredMap are persisted
     * @param classCatalog location to store class descriptions
     * @return StoredMap backed by the database and classCatalog
     * @throws com.sleepycat.je.DatabaseException
     *          if the StoredMap cannot be opened.
     */
    public StoredMap<byte[], byte[]> createStoredMapViewOfDatabase(Database database, StoredClassCatalog classCatalog, StreamingMarshaller marshaller) throws DatabaseException {
        EntryBinding<byte[]> storedEntryKeyBinding = new ByteArrayBinding();
        EntryBinding<byte[]> storedEntryValueBinding = new ByteArrayBinding();
        try {
            return new StoredMap<byte[], byte[]>(database,
                    storedEntryKeyBinding, storedEntryValueBinding, true);
        } catch (Exception caught) {
            caught = ExceptionUnwrapper.unwrap(caught);
            throw new CacheException("Error opening stored map", caught);
        }
    }

    /**
     * create a {@link com.sleepycat.collections.StoredMap} persisted by the <code>database</code>
     *
     * @param database     where entries in the StoredMap are persisted
     * @param classCatalog location to store class descriptions
     * @return StoredMap backed by the database and classCatalog
     * @throws com.sleepycat.je.DatabaseException
     *          if the StoredMap cannot be opened.
     */
    public StoredSortedMap<Long, byte[]> createStoredSortedMapForKeyExpiry(Database database, StoredClassCatalog classCatalog, StreamingMarshaller marshaller) throws DatabaseException {
        EntryBinding<Long> expiryKeyBinding = new LongBinding();
        EntryBinding<byte[]> expiryValueBinding = new ByteArrayBinding();
        try {
            return new StoredSortedMap<Long, byte[]>(database,
                    expiryKeyBinding, expiryValueBinding, true);
        } catch (Exception caught) {
            caught = ExceptionUnwrapper.unwrap(caught);
            throw new CacheException("error opening stored map", caught);
        }

    }
}