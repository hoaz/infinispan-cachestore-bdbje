package org.infinispan.loaders.bdbje;

import java.io.File;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.persistence.FlushingAsyncStoreTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "persistence.bdbje.BdbjeFlushingAsyncStoreTest", singleThreaded = true)
public class BdbjeFlushingAsyncStoreTest extends FlushingAsyncStoreTest {
    
    private File tmpDir;

    @Override
    protected void setup() throws Exception {
        tmpDir = new File(TestingUtil.tmpDirectory(this.getClass()));
        super.setup();
    }

    @Override
    protected void teardown() {
        super.teardown();
        if (tmpDir.exists()) {
            TestingUtil.recursiveFileRemove(tmpDir);
        }
    }

    @Override
    protected EmbeddedCacheManager createCacheManager() throws Exception {
       ConfigurationBuilder config = getDefaultStandaloneCacheConfig(false);
       config
          .persistence()
             .addStore(BdbjeCacheStoreConfigurationBuilder.class)
                .async().enable().threadPoolSize(1)
          .build();
       return TestCacheManagerFactory.createCacheManager(config);
    }

}
