package com.reactiveminds.genai.utils;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class Item<K,V> implements Delayed {
	final K key;
	public Item(K key, Duration ttl) {
		super();
		this.key = key;
		this.ttl = ttl;
		touch();
	}

	private V value;
	private final AtomicLong lastTouched = new AtomicLong();
	private final Duration ttl;

	@Override
	public int compareTo(Delayed o) {
		return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
	}
	private long elapsed() {
		return System.currentTimeMillis()-lastTouched.get();
	}
	private void touch() {
		while(!lastTouched.compareAndSet(lastTouched.get(), System.currentTimeMillis())) {
			Thread.yield();
		}
	}

	@Override
	public long getDelay(TimeUnit unit) {
		return unit.toMillis(ttl.toMillis() - elapsed());
	}
	public V getValue() {
		touch();
		return value;
	}
	public void setValue(V value) {
		this.value = value;
		touch();
	}

}
