package measurements.support;

import algorithms.size.sp.SizeBST;
import algorithms.size.sp.SizeConcurrentSkipListMap;
import algorithms.size.sp.SizeHashTable;

import algorithms.size.handshakes.HandshakesBST;
import algorithms.size.handshakes.HandshakesConcurrentSkipListMap;
import algorithms.size.handshakes.HandshakesHashTable;
import algorithms.size.locks.stampedlock.StampedLockBST;
import algorithms.size.locks.stampedlock.StampedLockConcurrentSkipListMap;
import algorithms.size.locks.stampedlock.StampedLockHashTable;
import algorithms.size.optimistic.OptimisticSizeBST;
import algorithms.size.optimistic.OptimisticSizeConcurrentSkipListMap;
import algorithms.size.optimistic.OptimisticSizeHashTable;

import measurements.adapters.*;
import java.util.ArrayList;

public class Factories {
    // central list of factory classes for all supported data structures
    public static final ArrayList<SetFactory<Integer>> factories = new ArrayList<SetFactory<Integer>>();

    static {
        factories.add(new ConcurrentSkipListMapFactory<Integer>());
        factories.add(new LockFreeBSTFactory<Integer>());
        factories.add(new HashTableFactory<Integer>());

        factories.add(new SizeConcurrentSkipListMapFactory<Integer>());
        factories.add(new SizeBSTFactory<Integer>());
        factories.add(new SizeHashTableFactory<Integer>());

        factories.add(new OptimisticSizeConcurrentSkipListMapFactory<Integer>());
        factories.add(new OptimisticSizeBSTFactory<Integer>());
        factories.add(new OptimisticSizeHashTableFactory<Integer>());

        factories.add(new HandshakesConcurrentSkipListMapFactory<Integer>());
        factories.add(new HandshakesBSTFactory<Integer>());
        factories.add(new HandshakesHashTableFactory<Integer>());

        factories.add(new StampedLockConcurrentSkipListMapFactory<Integer>());
        factories.add(new StampedLockBSTFactory<Integer>());
        factories.add(new StampedLockHashTableFactory<Integer>());
    }

    // factory classes for each supported data structure

    protected static class ConcurrentSkipListMapFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SkipListAdapter<K>();
        }

        public String getName() {
            return "SkipList";
        }
    }

    protected static class LockFreeBSTFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new BSTAdapter<K>();
        }

        public String getName() {
            return "BST";
        }
    }

    protected static class HashTableFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            if (param1 == null)
                throw new NullPointerException();
            return new HashTableAdapter<K>(param1);
        }

        public String getName() {
            return "HashTable";
        }
    }

    protected static class SizeConcurrentSkipListMapFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new SizeConcurrentSkipListMap<K, K>());
        }

        public String getName() {
            return "SizeSkipList";
        }
    }

    protected static class SizeBSTFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new SizeBST<K, K>());
        }

        public String getName() {
            return "SizeBST";
        }
    }

    protected static class SizeHashTableFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            if (param1 == null)
                throw new NullPointerException();
            return new SizeAdapter<K>(new SizeHashTable<K, K>(param1));
        }

        public String getName() {
            return "SizeHashTable";
        }
    }

    protected static class OptimisticSizeConcurrentSkipListMapFactory<K extends Comparable<? super K>>
            extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            if (param2 == null)
                return new SizeAdapter<K>(new OptimisticSizeConcurrentSkipListMap<K, K>(3));
            return new SizeAdapter<K>(new OptimisticSizeConcurrentSkipListMap<K, K>(param2));
        }

        public String getName() {
            return "OptimisticSizeSkipList";
        }
    }

    protected static class OptimisticSizeBSTFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            if (param2 == null)
                return new SizeAdapter<K>(new OptimisticSizeBST<K, K>(3));
            return new SizeAdapter<K>(new OptimisticSizeBST<K, K>(param2));
        }

        public String getName() {
            return "OptimisticSizeBST";
        }
    }

    protected static class OptimisticSizeHashTableFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            if (param2 == null)
                return new SizeAdapter<K>(new OptimisticSizeHashTable<K, K>(param1, 3));
            return new SizeAdapter<K>(new OptimisticSizeHashTable<K, K>(param1, param2));
        }

        public String getName() {
            return "OptimisticSizeHashTable";
        }
    }

    protected static class HandshakesConcurrentSkipListMapFactory<K extends Comparable<? super K>>
            extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new HandshakesConcurrentSkipListMap<K, K>());
        }

        public String getName() {
            return "HandshakesSkipList";
        }
    }

    protected static class HandshakesBSTFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new HandshakesBST<K, K>());
        }

        public String getName() {
            return "HandshakesBST";
        }
    }

    protected static class HandshakesHashTableFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new HandshakesHashTable<K, K>(param1));
        }

        public String getName() {
            return "HandshakesHashTable";
        }
    }

    protected static class StampedLockConcurrentSkipListMapFactory<K extends Comparable<? super K>>
            extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new StampedLockConcurrentSkipListMap<K, K>());
        }

        public String getName() {
            return "StampedLockSkipList";
        }
    }

    protected static class StampedLockBSTFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new StampedLockBST<K, K>());
        }

        public String getName() {
            return "StampedLockBST";
        }
    }

    protected static class StampedLockHashTableFactory<K extends Comparable<? super K>> extends SetFactory<K> {
        public SetInterface<K> newSet(final Integer param1, final Integer param2) {
            return new SizeAdapter<K>(new StampedLockHashTable<K, K>(param1));
        }

        public String getName() {
            return "StampedLockHashTable";
        }
    }
}