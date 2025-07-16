package algorithms.size.barrier;

/**
 * Implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
 *
 * This file applies the size transformation using the handshakes methodology
 * to the class `algorithms.baseline.HashTable`,
 * a hash table implemented as an array of linked lists. Its implementation is based on
 * the linked list used in the base level of `java.util.concurrent.ConcurrentSkipListMap`
 * by Doug Lea.
 *
 * The transformation is based on the wait-free size implementation `algorithms.size.sp.SizeHashTable`
 * from the paper "Concurrent Size" by Gal Sela and Erez Petrank.
 * 
 * Copyright (C) 2025 Hen Kas-Sharir  
 * Contact: henshar12@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import algorithms.size.core.SizeSet;
import algorithms.size.core.UpdateInfo;
import algorithms.size.core.UpdateOperations;
import algorithms.size.barrier.core.BarrierSizeCalculator;
import algorithms.size.barrier.core.SizePhases;
import jdk.internal.vm.annotation.Contended;
import measurements.support.ThreadID;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Comparator;

public class BarrierHashTable<K, V> implements SizeSet<K, V> {
    /**
     * The comparator used to maintain order in this map, or null if
     * using natural ordering.  (Non-private to simplify access in
     * nested classes.)
     */
    final Comparator<? super K> comparator;

    private final int tableSize;
    private final Node<K, V>[] table;

    @Contended
    private final transient BarrierSizeCalculator sizeCalculator = new BarrierSizeCalculator(this::cleanListOfUpdateInfoFields);

    /* ------ Taken from https://github.com/openjdk/jdk/blob/dc7d30d08eacbe4d00d16b13e921359d38c77cd8/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java ------ */

    private static final int MAXIMUM_CAPACITY = 1 << 30;
    static final int HASH_BITS = 0x7fffffff;

    /**
     * Spreads (XORs) higher bits of hash to lower and also forces top
     * bit to 0. Because the table uses power-of-two masking, sets of
     * hashes that vary only in bits above the current mask will
     * always collide. (Among known examples are sets of Float keys
     * holding consecutive whole numbers in small tables.)  So we
     * apply a transform that spreads the impact of higher bits
     * downward. There is a tradeoff between speed, utility, and
     * quality of bit-spreading. Because many common sets of hashes
     * are already reasonably distributed (so don't benefit from
     * spreading), and because we use trees to handle large sets of
     * collisions in bins, we just XOR some shifted bits in the
     * cheapest possible way to reduce systematic lossage, as well as
     * to incorporate impact of the highest bits that would otherwise
     * never be used in index calculations because of table bounds.
     */
    static final int spread(int h) {
        return (h ^ (h >>> 16)) & HASH_BITS;
    }

    /**
     * Returns a power of two table size for the given desired capacity.
     * See Hackers Delight, sec 3.2
     */
    private static final int tableSizeFor(int c) {
        int n = -1 >>> Integer.numberOfLeadingZeros(c - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /* ---------------- Node -------------- */

    /**
     * Nodes hold keys and values, and are singly linked in sorted
     * order, possibly with some intervening marker nodes. The list is
     * headed by a header node accessible as head.node. Headers and
     * marker nodes have null keys. The val field (but currently not
     * the key field) is nulled out upon deletion.
     */
    static final class Node<K, V> {
        final K key;
        Object valOrRemoveInfo;
        Node<K, V> next;
        volatile UpdateInfo insertInfo;

        Node(K key, V value, Node<K, V> next, UpdateInfo insertInfo) {
            this.key = key;
            this.valOrRemoveInfo = value;
            this.next = next;
            this.insertInfo = insertInfo;
        }

        // For head and marker nodes
        Node(K key, V value, Node<K, V> next) {
            this(key, value, next, null);
        }
    }

    /* ---------------- Constructors -------------- */

    /**
     * Constructs a new, empty map, sorted according to the
     * {@linkplain Comparable natural ordering} of the keys.
     */
    public BarrierHashTable(int requestedTableSize) {
        this(requestedTableSize, null);
    }

    /**
     * Constructs a new, empty map, sorted according to the specified
     * comparator.
     *
     * @param comparator the comparator that will be used to order this map.
     *        If {@code null}, the {@linkplain Comparable natural
     *        ordering} of the keys will be used.
     */
    public BarrierHashTable(int requestedTableSize, Comparator<? super K> comparator) {
        this.comparator = comparator;

        if (requestedTableSize <= 0) throw new NegativeArraySizeException();
        this.tableSize = tableSizeFor(requestedTableSize);
        Node<K, V>[] constructedTable = new Node[tableSize];
        // Initialize each of the table cells with a dummy head for its linked list:
        for (int i = 0; i < tableSize; ++i) {
            constructedTable[i] = new Node<K, V>(null, null, null);
        }
        this.table = constructedTable;
        // Now the table's content is visible to all, see https://stackoverflow.com/questions/2830739/do-the-up-to-date-guarantees-for-values-of-javas-final-fields-extend-to-indir
    }

    private Node<K, V> getListHead(Object key) {
        int keyHash = spread(key.hashCode());
        int tableIndex = (tableSize - 1) & keyHash;
        return table[tableIndex];
    }

    /* ---------------- List traversal -------------- */

    /**
     * Gets value for key. Same idea as findNode, except skips over
     * deletions and markers, and returns first encountered value to
     * avoid possibly inconsistent rereads.
     *
     * @param key the key
     * @return the value, or null if absent
     */
    private V slow_listDoGet(Object key, Node<K, V> head) {
        VarHandle.acquireFence();
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        V result = null;
        Node<K, V> b = head;
        Node<K, V> n;
        while ((n = b.next) != null) {
            int c;
            K k = n.key;
            Object valOrRemoveInfo;
            if ((valOrRemoveInfo = n.valOrRemoveInfo) == null || k == null ||
                    (c = cpr(cmp, key, k)) > 0) {
                // Cannot simply advance b in case remove info is installed in n.valOrRemoveInfo similarly to LinkedList,
                // because then if c==0, the removal of n must be reported to sizeCalculator before returning null.
                b = n;
            } else {
                if (c == 0) {
                    if (valOrRemoveInfo instanceof UpdateInfo)
                        sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.REMOVE, (UpdateInfo) n.valOrRemoveInfo);
                    else {
                        UpdateInfo insertInfo = n.insertInfo;
                        if (insertInfo != null) {
                            sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                            n.insertInfo = null;
                        }
                        result = (V) valOrRemoveInfo;
                    }
                }
                break;
            }
        }

        return result;
    }

    private V fast_listDoGet(Object key, Node<K, V> head) {
        VarHandle.acquireFence();
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        V result = null;
        Node<K, V> b = head;
        Node<K, V> n;
        while ((n = b.next) != null) {
            Object v;
            int c;
            K k = n.key;
            if ((v = n.valOrRemoveInfo) == null || k == null ||
                    (c = cpr(cmp, key, k)) > 0)
                b = n;
            else {
                if (c == 0)
                    result = (V) v; // possibly null, if n is removed
                break;
            }
        }

        return result;
    }

    /* ---------------- List insertion -------------- */

    /**
     * Main insertion method.  Adds element if not present, or
     * replaces value if present and onlyIfAbsent is false.
     *
     * @param key the key
     * @param value the value that must be associated with key
     * @param onlyIfAbsent if should not insert if already present
     * @return the old value, or null if newly inserted
     */
    private V slow_listDoPut(K key, V value, boolean onlyIfAbsent, Node<K, V> head) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        for (;;) {
            VarHandle.acquireFence();
            Node<K, V> b = head;
            for (;;) {                       // find insertion point
                Node<K, V> n, p;
                K k;
                Object valOrRemoveInfo;
                int c;
                if ((n = b.next) == null) {
                    if (b.key == null)       // if empty, type check key now
                        cpr(cmp, key, key);
                    c = -1;
                } else if ((k = n.key) == null)
                    break;                   // can't append; restart
                else if ((valOrRemoveInfo = n.valOrRemoveInfo) == null) {
                    fast_completeRemove(b, n);
                    c = 1;
                } else if (valOrRemoveInfo instanceof UpdateInfo) {
                    slow_completeRemove(b, n);
                    c = 1;
                } else if ((c = cpr(cmp, key, k)) > 0)
                    b = n;
                else if (c == 0 &&
                        (onlyIfAbsent || VAL_OR_REMOVE_INFO.compareAndSet(n, valOrRemoveInfo, value))) {
                    // In case n.val was CASed and n's insertion was not yet linearized then, the current insert is linearized right after that insertion
                    UpdateInfo insertInfo = n.insertInfo;
                    if (insertInfo != null) {
                        sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                        n.insertInfo = null;
                    }
                    return (V) valOrRemoveInfo;
                }

                UpdateInfo insertInfo;
                if (c < 0 &&
                        NEXT.compareAndSet(b, n, p = new Node<K, V>(key, value, n, insertInfo = sizeCalculator.createUpdateInfo(UpdateOperations.OpKind.Separated.INSERT)))) {
                    sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                    p.insertInfo = null;
                    return null;
                }
            }
        }
    }

    private V fast_listDoPut(K key, V value, boolean onlyIfAbsent, Node<K, V> head) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        for (;;) {
            VarHandle.acquireFence();
            Node<K, V> b = head;
            for (;;) {                       // find insertion point
                Node<K, V> n;
                K k;
                Object v;
                int c;
                if ((n = b.next) == null) {
                    if (b.key == null)       // if empty, type check key now
                        cpr(cmp, key, key);
                    c = -1;
                } else if ((k = n.key) == null)
                    break;                   // can't append; restart
                else if ((v = n.valOrRemoveInfo) == null) {
                    fast_completeRemove(b, n);
                    c = 1;
                } else if ((c = cpr(cmp, key, k)) > 0)
                    b = n;
                else if (c == 0 &&
                        (onlyIfAbsent || VAL_OR_REMOVE_INFO.compareAndSet(n, v, value))) {
                    return (V) v;
                }

                if (c < 0 &&
                        NEXT.compareAndSet(b, n, new Node<K, V>(key, value, n))) {
                    sizeCalculator.fast_updateMetadata(UpdateOperations.OpKind.INSERT, ThreadID.threadID.get());
                    return null;
                }
            }
        }
    }

    /* ---------------- List deletion -------------- */

    /**
    Slow path delete method.
    */
    final V slow_listDoRemove(Object key, Object value, Node<K, V> head) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        V result = null;
        outer: for (;;) { // Each iteration starts a traversal from the head
            VarHandle.acquireFence();
            Node<K, V> b = head;
            for (;;) { // Each iteration advances the pointers one step ahead in the list
                Node<K, V> n;
                K k;
                Object valOrRemoveInfo;
                int c;
                if ((n = b.next) == null)
                    break outer;
                else if ((k = n.key) == null)
                    break;
                else if ((valOrRemoveInfo = n.valOrRemoveInfo) == null) {
                    fast_completeRemove(b, n);
                } else if (valOrRemoveInfo instanceof UpdateInfo)
                    slow_completeRemove(b, n);
                else if ((c = cpr(cmp, key, k)) > 0)
                    b = n;
                else if (c < 0)
                    break outer;
                else if (value != null && !value.equals(valOrRemoveInfo))
                    // Either n's insert is linearized and so n.val==value is linearized too, or n's insert is not linearized.
                    // Thus, the remove should anyhow fail, and there is no need to call updateMetadataAndLinearize for the insert.
                    break outer;
                else {
                    UpdateInfo insertInfo = n.insertInfo;
                    if (insertInfo != null) {
                        sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                        n.insertInfo = null;
                    }
                    if (VAL_OR_REMOVE_INFO.compareAndSet(n, valOrRemoveInfo, sizeCalculator.createUpdateInfo(UpdateOperations.OpKind.Separated.REMOVE))) {
                        result = (V) valOrRemoveInfo;
                        slow_completeRemove(b, n);
                        break outer;
                    }
                }
            }
        }
        return result;
    }

    /**
    Fast path delete method.
    */
    final V fast_listDoRemove(Object key, Object value, Node<K, V> head) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        V result = null;
        outer:
        for (;;) { // Each iteration starts a traversal from the head
            VarHandle.acquireFence();
            Node<K, V> b = head;
            for (;;) { // Each iteration advances the pointers one step ahead in the list
                Node<K, V> n;
                K k;
                Object v;
                int c;
                if ((n = b.next) == null)
                    break outer;
                else if ((k = n.key) == null)
                    break;
                else if ((v = n.valOrRemoveInfo) == null)
                    fast_completeRemove(b, n);
                else if ((c = cpr(cmp, key, k)) > 0)
                    b = n;
                else if (c < 0)
                    break outer;
                else if (value != null && !value.equals((V) v))
                    break outer;
                else if (VAL_OR_REMOVE_INFO.compareAndSet(n, v, null)) {
                    result = (V) v;
                    sizeCalculator.fast_updateMetadata(UpdateOperations.OpKind.REMOVE, ThreadID.threadID.get());
                    fast_completeRemove(b, n);
                    break outer;
                }
            }
        }
        return result;
    }

    /* ----------------  Utilities -------------- */

    /**
     * Checks If a thread should operate in the slow path or fast path by parity of the size phase.
     */
    private boolean useFastPath(long sizePhase) {
        return (sizePhase & 1) == 0;
    }

    /**
     * Compares using comparator or natural ordering if null.
     * Called only by methods that have performed required type checks.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int cpr(Comparator c, Object x, Object y) {
        return (c != null) ? c.compare(x, y) : ((Comparable)x).compareTo(y);
    }

    /**
     * Tries to unlink deleted node n from predecessor b (if both
     * exist), by first splicing in a marker if not already present.
     * Upon return, node n is sure to be unlinked from b, possibly
     * via the actions of some other thread.
     *
     * 	1. Update sizeCalculator and linearize remove
     *  2. Insert marker node succeeding n
     *  3. Unlink n
     *
     * @param b if nonnull, predecessor
     * @param n if nonnull, node known to be deleted
     */
    private void slow_completeRemove(Node<K, V> b, Node<K, V> n) {
        if (b != null && n != null) {
            sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.REMOVE, (UpdateInfo) n.valOrRemoveInfo);

            Node<K, V> f, p;
            for (;;) {
                if ((f = n.next) != null && f.key == null) {
                    p = f.next;               // already marked
                    break;
                } else if (NEXT.compareAndSet(n, f,
                        new Node<K, V>(null, null, f))) {
                    p = f;                    // add marker
                    break;
                }
            }
            NEXT.compareAndSet(b, n, p);
        }
    }

    private static <K, V> void fast_completeRemove(Node<K, V> b, Node<K, V> n) {
        if (b != null && n != null) {
            Node<K, V> f, p;
            for (;;) {
                if ((f = n.next) != null && f.key == null) {
                    p = f.next;               // already marked
                    break;
                } else if (NEXT.compareAndSet(n, f,
                        new Node<K, V>(null, null, f))) {
                    p = f;                    // add marker
                    break;
                }
            }
            NEXT.compareAndSet(b, n, p);
        }
    }

    /* ------ Map API methods ------ */

    /**
     * Returns {@code true} if this map contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the specified key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     */
    public boolean containsKey(Object key) {
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doGet(key);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doGet(key);
        }
        sizeCalculator.leaveTheBarrier();
        return ret != null;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key} compares
     * equal to {@code k} according to the map's ordering, then this
     * method returns {@code v}; otherwise it returns {@code null}.
     * (There can be at most one such mapping.)
     *
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     */
    public V get(Object key) {
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doGet(key);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doGet(key);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }
    
    private V slow_doGet(Object key) {
        return slow_listDoGet(key, getListHead(key));
    }

    private V fast_doGet(Object key) {
        return fast_listDoGet(key, getListHead(key));
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or the given defaultValue if this map contains no mapping for the key.
     *
     * @param key the key
     * @param defaultValue the value to return if this map contains
     * no mapping for the given key
     * @return the mapping for the key, if present; else the defaultValue
     * @throws NullPointerException if the specified key is null
     * @since 1.8
     */
    public V getOrDefault(Object key, V defaultValue) {
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doGet(key);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doGet(key);
        }
        sizeCalculator.leaveTheBarrier();
        return ret == null ? defaultValue : ret;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key or value is null
     */
    public V put(K key, V value) {
        if (value == null)
            throw new NullPointerException();
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doPut(key, value, false);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doPut(key, value, false);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }
    
    private V slow_doPut(K key, V value, boolean onlyIfAbsent) {
        return slow_listDoPut(key, value, onlyIfAbsent, getListHead(key));
    }

    private V fast_doPut(K key, V value, boolean onlyIfAbsent) {
        return fast_listDoPut(key, value, onlyIfAbsent, getListHead(key));
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key for which mapping should be removed
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     */
    public V remove(Object key) {
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doRemove(key, null);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doRemove(key, null);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }
    
    private V slow_doRemove(Object key, Object value) {
        return slow_listDoRemove(key, value, getListHead(key));
    }

    private V fast_doRemove(Object key, Object value) {
        return fast_listDoRemove(key, value, getListHead(key));
    }

    /* ------ ConcurrentMap API methods ------ */

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or {@code null} if there was no mapping for the key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key or value is null
     */
    public V putIfAbsent(K key, V value) {
        if (value == null)
            throw new NullPointerException();
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doPut(key, value, true);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doPut(key, value, true);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        if (key == null)
            throw new NullPointerException();
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_doRemove(key, value);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_doRemove(key, value);
        }
        sizeCalculator.leaveTheBarrier();
        return value != null && ret != null;
    }

    /* ------ SortedMap API methods ------ */

    public Comparator<? super K> comparator() {
        return comparator;
    }

    /**
     * Returns the current size of the map.
     */
    public int size() {
        long c;
        return ((c = sizeCalculator.compute()) >= Integer.MAX_VALUE) ?
                Integer.MAX_VALUE : (int) c;
    }

    // VarHandle mechanics
    private static final VarHandle NEXT;
    private static final VarHandle VAL_OR_REMOVE_INFO;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            NEXT = l.findVarHandle(Node.class, "next", Node.class);
            VAL_OR_REMOVE_INFO = l.findVarHandle(Node.class, "valOrRemoveInfo", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /* For cleaning the hash table of UpdateInfo fields
     * Only one thread can execute concurrently
    */
    public boolean cleanListOfUpdateInfoFields() {
        for (int i = 0; i < tableSize; i++) {
            Node<K, V> b = table[i];
            for (;;) {                       // find insertion point
                Node<K, V> n;
                Object v;
                if ((n = b.next) == null) {
                    break;
                } else if ((v = n.valOrRemoveInfo) == null || v instanceof UpdateInfo) {
                    VAL_OR_REMOVE_INFO.compareAndSet(n, v, null);
                    fast_completeRemove(b, n);
                } else
                    b = n;
            }
        }
        return true;
    }


    // For debug
    public long getSumOfKeys() {
        Node<K, V> n;
        long keysSum = 0;
        for (int i = 0; i < tableSize; i++) {
            Node<K, V> b = table[i];
            while ((n = b.next) != null) {
                K k = n.key;
                Object v;
                if (k != null && !((v = n.valOrRemoveInfo) == null || v instanceof UpdateInfo)) // not accurate if concurrent with remove, since remove info is installed in n.valOrRemoveInfo before n's remove is linearized
                    keysSum += (Integer) k;
                b = n;
            }
        }
        return keysSum;
    }
}
