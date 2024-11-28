package com.reactiveminds.genai.utils;

import java.util.concurrent.Callable;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;

public class MyCacheManager extends ConcurrentMapCacheManager implements CacheManager{
	@Override
	protected Cache createConcurrentMapCache(String name) {
		return new AbstractValueAdaptingCache(false) {
			
			@Override
			public void put(Object key, Object value) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public Object getNativeCache() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getName() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public <T> T get(Object key, Callable<T> valueLoader) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public void evict(Object key) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void clear() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			protected Object lookup(Object key) {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}

}
