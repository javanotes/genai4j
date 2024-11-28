package com.reactiveminds.genai.utils;

import java.io.Closeable;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.util.Assert;

class ExpirableMap<K,V> implements Map<K, V>,Closeable,Runnable{
	private final DelayQueue<Item<K, V>> maxHeap = new DelayQueue<>();
	private Map<K, Item<K, V>> map = null;
	private Thread expiryWorker;
	private AtomicBoolean running = new AtomicBoolean();
	private final ReadWriteLock[] segmentLocks;
	private final Duration timeToIdle;
	/**
	 * 
	 * @param name
	 */
	public ExpirableMap(String name){
		this(name, Duration.ofMillis(Long.MAX_VALUE));
	}
	/**
	 * 
	 * @param name
	 * @param timeToIdle
	 */
	public ExpirableMap(String name, Duration timeToIdle){
		this(name, timeToIdle, 16);
	}
	protected Map<K, Item<K, V>> createTheMap() {
		return new ConcurrentHashMap<>();
	}
	/**
	 * 
	 * @param name
	 * @param timeToIdle
	 * @param segments
	 */
	public ExpirableMap(String name, Duration timeToIdle, int segments) {
		super();
		this.timeToIdle = timeToIdle;
		segmentLocks = new ReadWriteLock[segments];
		for (int i = 0; i < segmentLocks.length; i++) {
			segmentLocks[i] = new ReentrantReadWriteLock();
		}
		expiryWorker = new Thread(() -> {
		
			while (running.get()) {
				try {
					Item<K, V> item = maxHeap.take();
					Lock l = lock(item.key);
					try {
						map.remove(item.key);
					} finally {
						l.unlock();
					}
				} 
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				
			}
			
		}, name.concat(".expiry"));
		expiryWorker.setDaemon(true);
		
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public V get(Object key) {
		Assert.notNull(key, "null key");
		Lock l = lock((K) key, false);
		try {
			Item<K, V> item = map.get(key);
			if(item != null) {
				maxHeap.remove(item);
				V old = item.getValue();		
				maxHeap.add(item);
				return old;
			}
		} 
		finally {
			l.unlock();
		}
		return null;
	}

	private Lock lock(K key, boolean w) {
		ReadWriteLock rwLock = segmentLocks[String.valueOf(key).hashCode() % segmentLocks.length];
		Lock l = w ? rwLock.writeLock() : rwLock.readLock();
		l.lock();
		return l;
	}
	@Override
	public V put(K key, V value) {
		Assert.notNull(key, "null key");
		Lock l = lock(key);
		try {
			Item<K, V> item = map.get(key);
			if (item != null) {
				maxHeap.remove(item);
				V old = item.getValue();		
				item.setValue(value);
				maxHeap.add(item);
				return old;
			}
			item = new Item<>(key, timeToIdle);
			item.setValue(value);
			map.put(key, item);
			maxHeap.add(item);
			return null;
		} 
		finally {
			l.unlock();
		}
	}

	private Lock lock(K key) {
		return lock(key, true);
	}

	@Override
	public V remove(Object key) {
		Assert.notNull(key, "null key");
		Lock l = lock((K) key);
		try {
			Item<K, V> item = map.remove(key);
			if(item != null) {
				maxHeap.remove(item);
			}
			return item != null ? item.getValue() : null;
		} 
		finally {
			l.unlock();
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		m.entrySet().parallelStream().forEach(e -> this.put(e.getKey(), e.getValue()));
		
	}

	@Override
	public void clear() {
		keySet().parallelStream().forEach(this::remove);		
	}

	@Override
	public Set<K> keySet() {
		return map.keySet();
	}

	@Override
	public Collection<V> values() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void close() {
		if(running.compareAndSet(true, false)) {
			expiryWorker.interrupt();
			try {
				expiryWorker.join(2000);
			} catch (InterruptedException e) {
				// no-op
				e.printStackTrace();
			}
		}		
		
	}
	@Override
	public void run() {
		if(running.compareAndSet(false, true)) {
			map = createTheMap();
			expiryWorker.start();
		}
	}

}
