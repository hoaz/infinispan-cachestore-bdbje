package org.infinispan.loaders.bdbje.configuration;

import java.util.Properties;

import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.cache.SingletonStoreConfiguration;
import org.infinispan.loaders.bdbje.BdbjeCacheStore;
import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;

@BuiltBy(BdbjeCacheStoreConfigurationBuilder.class)
@ConfigurationFor(BdbjeCacheStore.class)
public class BdbjeCacheStoreConfiguration extends AbstractStoreConfiguration {

   private final String location;
   private final long lockAcquistionTimeout;
   private final int maxTxRetries;
   private final String cacheDbNamePrefix;
   private final String catalogDbName;
   private final String expiryDbPrefix;
   private final String environmentPropertiesFile;

   public BdbjeCacheStoreConfiguration(String location, long lockAcquistionTimeout, int maxTxRetries,
         String cacheDbNamePrefix, String catalogDbName, String expiryDbPrefix, String environmentPropertiesFile,
         boolean purgeOnStartup, boolean fetchPersistentState, boolean ignoreModifications,
         AsyncStoreConfiguration async, SingletonStoreConfiguration singletonStore, boolean preload,
         boolean shared, Properties properties) {
      super(purgeOnStartup, fetchPersistentState, ignoreModifications, async, singletonStore, preload, shared, properties);
      this.location = location;
      this.lockAcquistionTimeout = lockAcquistionTimeout;
      this.maxTxRetries = maxTxRetries;
      this.cacheDbNamePrefix = cacheDbNamePrefix;
      this.catalogDbName = catalogDbName;
      this.expiryDbPrefix = expiryDbPrefix;
      this.environmentPropertiesFile = environmentPropertiesFile;

   }

   public String location() {
      return location;
   }

   public long lockAcquisitionTimeout() {
      return lockAcquistionTimeout;
   }

   public int maxTxRetries() {
      return maxTxRetries;
   }

   public String cacheDbNamePrefix() {
      return cacheDbNamePrefix;
   }

   public String catalogDbName() {
      return catalogDbName;
   }

   public String expiryDbPrefix() {
      return expiryDbPrefix;
   }

   public String environmentPropertiesFile() {
      return environmentPropertiesFile;
   }

}
