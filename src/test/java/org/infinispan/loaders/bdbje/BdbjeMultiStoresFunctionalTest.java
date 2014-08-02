package org.infinispan.loaders.bdbje;

import java.io.File;

import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.persistence.MultiStoresFunctionalTest;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "persistence.bdbje.BdbjeMultiStoresFunctionalTest")
public class BdbjeMultiStoresFunctionalTest extends MultiStoresFunctionalTest<BdbjeCacheStoreConfigurationBuilder> {

    private File tmpDir;

    @BeforeClass(alwaysRun = true)
    protected void setPaths() {
        tmpDir = new File(TestingUtil.tmpDirectory(this.getClass()));
    }

    @BeforeMethod
    protected void cleanDataFiles() {
        if (tmpDir.exists()) {
            TestingUtil.recursiveFileRemove(tmpDir);
        }
    }

    @Override
    protected BdbjeCacheStoreConfigurationBuilder buildCacheStoreConfig(PersistenceConfigurationBuilder builder, String discriminator)
            throws Exception {
        return builder.addStore(BdbjeCacheStoreConfigurationBuilder.class)
                  .location(new File(tmpDir, discriminator).getAbsolutePath());
    }

}
