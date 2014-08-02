package org.infinispan.loaders.bdbje;

import java.io.File;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.factories.KnownComponentNames;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.persistence.ParallelIterationTest;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "persistence.bdbje.BdbjeParallelIterationTest")
public class BdbjeParallelIterationTest extends ParallelIterationTest {

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
    protected void configurePersistence(ConfigurationBuilder cb) {
        cb.persistence().addStore(BdbjeCacheStoreConfigurationBuilder.class).location(tmpDir.getAbsolutePath());
    }

    @Override
    protected int numThreads() {
        return KnownComponentNames.getDefaultThreads(KnownComponentNames.PERSISTENCE_EXECUTOR);
    }

}
