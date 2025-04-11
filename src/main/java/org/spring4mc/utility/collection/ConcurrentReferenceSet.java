package org.spring4mc.utility.collection;

import lombok.experimental.Delegate;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.Collections;
import java.util.Set;

public class ConcurrentReferenceSet<V> implements Set<V> {
    @Delegate
    private final Set<V> delegate;

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;
    private static final ConcurrentReferenceHashMap.ReferenceType DEFAULT_REFERENCE_TYPE = ConcurrentReferenceHashMap.ReferenceType.SOFT;

    public ConcurrentReferenceSet(ConcurrentReferenceHashMap.ReferenceType referenceType) {
        this(DEFAULT_INITIAL_CAPACITY, referenceType);
    }

    public ConcurrentReferenceSet(int initialCapacity) {
        this(initialCapacity, DEFAULT_CONCURRENCY_LEVEL);
    }

    public ConcurrentReferenceSet(int initialCapacity, ConcurrentReferenceHashMap.ReferenceType referenceType) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL, referenceType);
    }

    public ConcurrentReferenceSet(int initialCapacity, int concurrencyLevel) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, concurrencyLevel, DEFAULT_REFERENCE_TYPE);
    }

    public ConcurrentReferenceSet(int initialCapacity, float loadFactor, int concurrencyLevel, ConcurrentReferenceHashMap.ReferenceType referenceType) {
        this.delegate = Collections.newSetFromMap(new ConcurrentReferenceHashMap<>(initialCapacity, loadFactor, concurrencyLevel, referenceType));
    }
}
