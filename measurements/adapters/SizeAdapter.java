package measurements.adapters;

import algorithms.size.core.SizeSet;
import measurements.support.SetInterface;

public class SizeAdapter<K extends Comparable<? super K>> extends AbstractAdapter<K> implements SetInterface<K> {
//    SizeHashTable<K,K> set;

    SizeSet<K, K> set;

    public SizeAdapter(SizeSet<K, K> set) {
        this.set = set;
    }

    @Override
    public boolean contains(K key) {
        return set.containsKey(key);
    }

    @Override
    public boolean insert(K key) {
        return set.putIfAbsent(key, key) == null;
    }

    @Override
    public boolean remove(K key) {
        return set.remove(key) != null;
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public long getKeysum() {
        return set.getSumOfKeys();
    }
}
