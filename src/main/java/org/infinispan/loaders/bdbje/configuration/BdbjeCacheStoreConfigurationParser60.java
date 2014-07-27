package org.infinispan.loaders.bdbje.configuration;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ConfigurationParser;
import org.infinispan.configuration.parsing.Namespace;
import org.infinispan.configuration.parsing.Namespaces;
import org.infinispan.configuration.parsing.ParseUtils;
import org.infinispan.configuration.parsing.Parser60;
import org.infinispan.configuration.parsing.XMLExtendedStreamReader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import static org.infinispan.commons.util.StringPropertyReplacer.replaceProperties;

/**
 *
 * BdbjeCacheStoreConfigurationParser60.
 *
 * @author Galder Zamarreño
 * @since 6.0
 */
@Namespaces({
   @Namespace(uri = "urn:infinispan:config:bdbje:6.0", root = "bdbjeStore"),
   @Namespace(root = "bdbjeStore"),
})
public class BdbjeCacheStoreConfigurationParser60 implements ConfigurationParser {
   public BdbjeCacheStoreConfigurationParser60() {
   }

   @Override
   public void readElement(final XMLExtendedStreamReader reader, final ConfigurationBuilderHolder holder)
         throws XMLStreamException {
      ConfigurationBuilder builder = holder.getCurrentConfigurationBuilder();

      Element element = Element.forName(reader.getLocalName());
      switch (element) {
      case BDBJE_STORE: {
         parseBdbjeStore(reader, builder.persistence(), holder.getClassLoader());
         break;
      }
      default: {
         throw ParseUtils.unexpectedElement(reader);
      }
      }
   }

   private void parseBdbjeStore(final XMLExtendedStreamReader reader, PersistenceConfigurationBuilder loadersBuilder,
         ClassLoader classLoader) throws XMLStreamException {
      BdbjeCacheStoreConfigurationBuilder builder = new BdbjeCacheStoreConfigurationBuilder(loadersBuilder);
      parseBdbjeStoreAttributes(reader, builder);

      while (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
         Parser60.parseCommonStoreChildren(reader, builder);
      }
      loadersBuilder.addStore(builder);
   }

   private void parseBdbjeStoreAttributes(XMLExtendedStreamReader reader, BdbjeCacheStoreConfigurationBuilder builder)
         throws XMLStreamException {
      for (int i = 0; i < reader.getAttributeCount(); i++) {
         ParseUtils.requireNoNamespaceAttribute(reader, i);
         String value = replaceProperties(reader.getAttributeValue(i));
         String attrName = reader.getAttributeLocalName(i);
         Attribute attribute = Attribute.forName(attrName);
         switch (attribute) {
         case CACHE_DB_NAME_PREFIX: {
            builder.cacheDbNamePrefix(value);
            break;
         }
         case CATALOG_DB_NAME: {
            builder.catalogDbName(value);
            break;
         }
         case ENVIRONMENT_PROPERTIES_FILE: {
            builder.environmentPropertiesFile(value);
            break;
         }
         case EXPIRY_DB_PREFIX: {
            builder.expiryDbPrefix(value);
            break;
         }
         case LOCATION: {
            builder.location(value);
            break;
         }
         case LOCK_ACQUISITION_TIMEOUT: {
            builder.lockAcquistionTimeout(Long.parseLong(value));
            break;
         }
         case MAX_TX_RETRIES: {
            builder.maxTxRetries(Integer.parseInt(value));
            break;
         }
         default: {
            Parser60.parseCommonStoreAttributes(reader, builder, attrName, value, i);
            break;
         }
         }
      }
   }
}
