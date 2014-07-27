package org.infinispan.loaders.bdbje;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.util.RuntimeExceptionWrapper;

import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.marshall.core.MarshalledEntry;

import java.io.IOException;

class InternalCacheEntryBinding implements EntryBinding<MarshalledEntry> {
   StreamingMarshaller m;

   InternalCacheEntryBinding(StreamingMarshaller m) {
      this.m = m;
   }

   @Override
   public MarshalledEntry entryToObject(DatabaseEntry entry) {
      try {
         return (MarshalledEntry) m.objectFromByteBuffer(entry.getData());
      } catch (IOException e) {
         throw new RuntimeExceptionWrapper(e);
      } catch (ClassNotFoundException e) {
         throw new RuntimeExceptionWrapper(e);
      }
   }

   @Override
   public void objectToEntry(MarshalledEntry object, DatabaseEntry entry) {
      byte[] b;
      try {
         b = m.objectToByteBuffer(object);
      } catch (IOException e) {
         throw new RuntimeExceptionWrapper(e);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
         throw new RuntimeExceptionWrapper(ie);
      }
      entry.setData(b);
   }
}
