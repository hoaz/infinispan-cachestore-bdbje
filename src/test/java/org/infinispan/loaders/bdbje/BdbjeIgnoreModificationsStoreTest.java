package org.infinispan.loaders.bdbje;

import java.io.File;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.persistence.IgnoreModificationsStoreTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(testName = "persistence.bdbje.BdbjeIgnoreModificationsStoreTest", groups = "functional", singleThreaded = true)
@CleanupAfterMethod
public class BdbjeIgnoreModificationsStoreTest extends IgnoreModificationsStoreTest {

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
       ConfigurationBuilder cfg = getDefaultStandaloneCacheConfig(true);
       cfg
          .invocationBatching().enable()
          .persistence()
             .addStore(BdbjeCacheStoreConfigurationBuilder.class)
                 .location(tmpDir.getAbsolutePath())
                 .ignoreModifications(true);

       return TestCacheManagerFactory.createCacheManager(cfg);
    }
}
