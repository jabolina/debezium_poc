package org.gingesnap.cdc.cache.hotrod;

import java.net.URI;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Singleton;

import org.gingesnap.cdc.CacheBackend;
import org.gingesnap.cdc.cache.CacheService;
import org.infinispan.client.hotrod.DataFormat;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.commons.dataconversion.MediaType;

import io.quarkus.arc.lookup.LookupIfProperty;

@LookupIfProperty(name = "service.hotrod.enabled", stringValue = "true", lookupIfMissing = true)
@Singleton
public class HotRodService implements CacheService {
   ConcurrentMap<URI, HotRodCacheBackend> otherURIs = new ConcurrentHashMap<>();

   @Override
   public CacheBackend backendForURI(URI uri) {
      if (!uri.getScheme().startsWith("hotrod")) {
         return null;
      }
      return otherURIs.computeIfAbsent(uri, innerURI -> {
         RemoteCacheManager remoteCacheManager = new RemoteCacheManager(innerURI);
         return new HotRodCacheBackend(remoteCacheManager.administration().getOrCreateCache("debezium-cache",
               new XMLStringConfiguration(
                     "<distributed-cache>" +
                        "<encoding>" +
                           "<key media-type=\"text/plain\"/>" +
                           "<value media-type=\"application/json\"/>" +
                        "</encoding>" +
                     "</distributed-cache>")));
      });
   }

   @Override
   public void shutdown() {
      for (Iterator<HotRodCacheBackend> valueIter = otherURIs.values().iterator(); valueIter.hasNext(); ) {
         HotRodCacheBackend backend = valueIter.next();
         backend.remoteCache.getRemoteCacheManager().stopAsync();
         valueIter.remove();
      }
   }
}