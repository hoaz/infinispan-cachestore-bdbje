package org.infinispan.loaders.bdbje;

import java.io.File;

import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.persistence.BaseStoreFunctionalTest;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "persistence.bdbje.BdbjeCacheStoreFunctionalTest")
public class BdbjeCacheStoreFunctionalTest extends BaseStoreFunctionalTest {
    
    private File tmpDir;

    @Override
    protected PersistenceConfigurationBuilder createCacheStoreConfig(PersistenceConfigurationBuilder persistence, boolean preload) {
        tmpDir = new File(TestingUtil.tmpDirectory(this.getClass()));
        persistence.addStore(BdbjeCacheStoreConfigurationBuilder.class).preload(preload).location(tmpDir.getAbsolutePath());
        return persistence;
    }

}
