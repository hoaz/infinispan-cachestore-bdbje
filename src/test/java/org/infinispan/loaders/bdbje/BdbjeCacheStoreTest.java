package org.infinispan.loaders.bdbje;

import org.infinispan.commons.io.ByteBufferFactoryImpl;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.marshall.core.MarshalledEntryFactoryImpl;
import org.infinispan.persistence.BaseStoreTest;
import org.infinispan.persistence.DummyInitializationContext;
import org.infinispan.persistence.spi.AdvancedLoadWriteStore;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "persistence.bdbje.BdbjeCacheStoreTest")
public class BdbjeCacheStoreTest extends BaseStoreTest {

    @Override
    protected AdvancedLoadWriteStore createStore() throws PersistenceException {
        BdbjeCacheStore cl = new BdbjeCacheStore();
        BdbjeCacheStoreConfigurationBuilder loader = TestCacheManagerFactory.getDefaultCacheConfiguration(false).persistence()
                .addStore(BdbjeCacheStoreConfigurationBuilder.class);
        cl.init(new DummyInitializationContext(loader.create(), getCache(), getMarshaller(), new ByteBufferFactoryImpl(),
                new MarshalledEntryFactoryImpl(getMarshaller())));
        cl.start();
        csc = loader.create();
        return cl;
    }

}
