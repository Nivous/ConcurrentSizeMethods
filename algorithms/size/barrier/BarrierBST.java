package algorithms.size.barrier;

/**
 * This is an implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
 *
 * This file applies the size transformation using the handshakes methodology
 * to the class `algorithms.baseline.BSTTransformedToRemoveLpAtMark`,
 * which is based on `algorithms.baseline.BST` — a variant of the
 * non-blocking concurrent binary search tree by Faith Ellen, Panagiota Fatourou,
 * Eric Ruppert, and Franck van Breugel, taken from Trevor Brown's implementation.
 *
 * The transformation is based on the wait-free size implementation `algorithms.size.sp.SizeBST`
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
import algorithms.size.core.UpdateInfoHolder;
import algorithms.size.core.UpdateOperations;
import algorithms.size.barrier.core.BarrierSizeCalculator;
import algorithms.size.barrier.core.SizePhases;
import jdk.internal.vm.annotation.Contended;
import measurements.support.ThreadID;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class BarrierBST<K extends Comparable<? super K>, V> implements SizeSet<K, V> {
    //--------------------------------------------------------------------------------
    // Class: Node, LeafNode, InternalNode
    //--------------------------------------------------------------------------------
    protected static abstract class Node<E extends Comparable<? super E>, V> {
        final E key;

        Node(final E key) {
            this.key = key;
        }
    }

    protected final static class LeafNode<E extends Comparable<? super E>, V> extends Node<E, V> {
        final V value;
        volatile UpdateInfo insertInfo;

        LeafNode(final E key, final V value, final UpdateInfo insertInfo) {
            super(key);
            this.value = value;
            this.insertInfo = insertInfo;
        }

        LeafNode(final E key, final V value) {
            super(key);
            this.value = value;
            this.insertInfo = null;
        }

        // For dummy nodes
        LeafNode() {
            this(null, null, null);
        }

        // For sibling nodes produced in insertions
        LeafNode(LeafNode<E, V> node) {
            this(node.key, node.value, node.insertInfo);
        }
    }

    protected final static class InternalNode<E extends Comparable<? super E>, V> extends Node<E, V> {
        volatile Node<E, V> left;
        volatile Node<E, V> right;
        volatile Info<E, V> info;

        InternalNode(final E key, final LeafNode<E, V> left, final LeafNode<E, V> right) {
            super(key);
            this.left = left;
            this.right = right;
            this.info = null;
        }
    }

    //--------------------------------------------------------------------------------
    // Class: Info, DInfo, IInfo, Mark, Clean
    //--------------------------------------------------------------------------------
    protected static abstract class Info<E extends Comparable<? super E>, V> {
    }

    protected final static class DInfo<E extends Comparable<? super E>, V> extends Info<E, V> implements UpdateInfoHolder {
        final InternalNode<E, V> p;
        final LeafNode<E, V> l;
        final InternalNode<E, V> gp;
        final Info<E, V> pinfo;

        final int removeTid;
        final long removeCount;

        DInfo(final LeafNode<E, V> leaf, final InternalNode<E, V> parent, final InternalNode<E, V> grandparent, final Info<E, V> pinfo,
              final int removeTid, final long removeCount) {
            this.p = parent;
            this.l = leaf;
            this.gp = grandparent;
            this.pinfo = pinfo;
            this.removeTid = removeTid;
            this.removeCount = removeCount;
        }

        @Override
        public int getTid() {
            return removeTid;
        }

        @Override
        public long getCounter() {
            return removeCount;
        }
    }

    protected final static class IInfo<E extends Comparable<? super E>, V> extends Info<E, V> {
        final InternalNode<E, V> p;
        final LeafNode<E, V> l;
        final Node<E, V> lReplacingNode;

        IInfo(final LeafNode<E, V> leaf, final InternalNode<E, V> parent, final Node<E, V> lReplacingNode) {
            this.p = parent;
            this.l = leaf;
            this.lReplacingNode = lReplacingNode;
        }
    }

    protected final static class Mark<E extends Comparable<? super E>, V> extends Info<E, V> {
        final DInfo<E, V> dinfo;

        Mark(final DInfo<E, V> dinfo) {
            this.dinfo = dinfo;
        }
    }

    protected final static class Clean<E extends Comparable<? super E>, V> extends Info<E, V> {
    }

    //--------------------------------------------------------------------------------
    // DICTIONARY
    //--------------------------------------------------------------------------------
    private static final AtomicReferenceFieldUpdater<InternalNode, Node> leftUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "left");
    private static final AtomicReferenceFieldUpdater<InternalNode, Node> rightUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Node.class, "right");
    private static final AtomicReferenceFieldUpdater<InternalNode, Info> infoUpdater = AtomicReferenceFieldUpdater.newUpdater(InternalNode.class, Info.class, "info");

    final InternalNode<K, V> root;
    
    @Contended
    private final BarrierSizeCalculator sizeCalculator = new BarrierSizeCalculator();

    public BarrierBST() {
        // to avoid handling special case when <= 2 nodes,
        // create 2 dummy nodes, both contain key null
        // All real keys inside BST are required to be non-null
        root = new InternalNode<K, V>(null, new LeafNode<K, V>(), new LeafNode<K, V>());
    }

    //--------------------------------------------------------------------------------
    // PUBLIC METHODS:
    //--------------------------------------------------------------------------------

    /** PRECONDITION: key CANNOT BE NULL **/
    public final boolean containsKey(final K key) {
        return slow_get(key) != null;
    }

    /** PRECONDITION: key CANNOT BE NULL **/
    public final V get(final K key) {
        return slow_get(key);
    }

    // Insert key to dictionary, returns the previous value associated with the specified key,
    // or null if there was no mapping for the key
    /** PRECONDITION: key, value CANNOT BE NULL **/
    public final V putIfAbsent(final K key, final V value) {
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path in even phases
            ret = fast_putIfAbsent(key, value);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_putIfAbsent(key, value);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }

    // Insert key to dictionary, return the previous value associated with the specified key,
    // or null if there was no mapping for the key
    /** PRECONDITION: key, value CANNOT BE NULL **/
    public final V put(final K key, final V value) {
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_put(key, value);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_put(key, value);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }

    // Delete key from dictionary, return the associated value when successful, null otherwise
    /** PRECONDITION: key CANNOT BE NULL **/
    public final V remove(final K key) {
        if (key == null) throw new NullPointerException();
        V ret;
        sizeCalculator.registerToTheBarrier();
        long currentSizePhase = sizeCalculator.getSizePhase();
        if (useFastPath(currentSizePhase)) { // Enter the fast path
            ret = fast_remove(key);
        } else { // A size operation is currently in progress. Switch to the slow path.
            ret = slow_remove(key);
        }
        sizeCalculator.leaveTheBarrier();
        return ret;
    }

    public int size() {
        long c;
        return ((c = sizeCalculator.compute()) >= Integer.MAX_VALUE) ?
                Integer.MAX_VALUE : (int) c;
    }

    //--------------------------------------------------------------------------------
    // PRIVATE METHODS
    // - slow_get
    // - slow_putIfAbsent
    // - fast_putIfAbsent
    // - slow_put
    // - fast_put
    // - slow_remove
    // - fast_remove
    // - slow_helpDelete
    // - fast_helpDelete
    // - slow_help
    // - fast_help
    // - slow_helpMarked
    // - fast_helpMarked

    // - helpInsert
    // - helpDelete
    // - help
    // - helpMarked
    //--------------------------------------------------------------------------------

    private final V slow_get(final K key) {
        if (key == null) throw new NullPointerException();
        InternalNode<K, V> p = root;
        Node<K, V> l = p.left;
        while (l.getClass() == InternalNode.class) {
            p = (InternalNode<K, V>) l;
            l = (l.key == null || key.compareTo(l.key) < 0) ? ((InternalNode<K, V>) l).left : ((InternalNode<K, V>) l).right;
        }
        if (l.key == null || key.compareTo(l.key) != 0) { // l is a dummy leaf, or l.key != key
            return null;
        }
        Info<K, V> pinfo = p.info;
        // We might obtain pinfo when p is no longer l's parent. In that case, we return l.value, and that is fine:
        // l might have already been removed by now, but only after its parent was changed from p to another node,
        // so l has been in the tree at the moment it was obtained from p's child pointer.
        if (pinfo != null && pinfo.getClass() == Mark.class && ((Mark<K, V>) pinfo).dinfo.l == l) { // l is being removed
            sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.REMOVE, ((Mark<K, V>) pinfo).dinfo);
            return null;
        }
        // l's insertion might be still ongoing
        UpdateInfo insertInfo = ((LeafNode<K, V>) l).insertInfo;
        if (insertInfo != null) {
            sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
            ((LeafNode<K, V>) l).insertInfo = null;
        }
        return ((LeafNode<K, V>) l).value;
    }

    private final V slow_putIfAbsent(final K key, final V value) {
        if (key == null || value == null) throw new NullPointerException();
        InternalNode<K, V> newInternal;
        LeafNode<K, V> newSibling, newNode;

        /** SEARCH VARIABLES **/
        InternalNode<K, V> p;
        Info<K, V> pinfo;
        Node<K, V> l;
        /** END SEARCH VARIABLES **/

        UpdateInfo newNodeInsertInfo = sizeCalculator.createUpdateInfo(UpdateOperations.OpKind.Separated.INSERT);
        newNode = new LeafNode<K, V>(key, value, newNodeInsertInfo);

        while (true) {

            /** SEARCH **/
            p = root;
            l = p.left;
            while (l.getClass() == InternalNode.class) {
                p = (InternalNode<K, V>) l;
                l = (p.key == null || key.compareTo(p.key) < 0) ? p.left : p.right;
            }
            pinfo = p.info;                             // read pinfo once instead of every iteration
            if (l != p.left && l != p.right) continue;  // then confirm the child link to l is valid
            // (just as if we'd read p's info field before the reference to l)
            /** END SEARCH **/

            LeafNode<K, V> foundLeaf = (LeafNode<K, V>) l;

            if (pinfo != null && pinfo.getClass() == Mark.class) {
                slow_helpMarked(((Mark<K, V>) pinfo).dinfo);
            } else if (key.equals(foundLeaf.key)) {
                UpdateInfo insertInfo = foundLeaf.insertInfo;
                if (insertInfo != null) {
                    sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                    foundLeaf.insertInfo = null;
                }
                return foundLeaf.value; // key already in the tree, no duplicate allowed
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                slow_help(pinfo);
            } else {
                newSibling = new LeafNode<K, V>(foundLeaf);
                if (foundLeaf.key == null || key.compareTo(foundLeaf.key) < 0)  // newinternal = max(ret.foundLeaf.key, key);
                    newInternal = new InternalNode<K, V>(foundLeaf.key, newNode, newSibling);
                else
                    newInternal = new InternalNode<K, V>(key, newSibling, newNode);

                final IInfo<K, V> newPInfo = new IInfo<K, V>(foundLeaf, p, newInternal);

                // try to IFlag parent
                if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) { // iflag step
                    helpInsert(newPInfo);
                    sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, newNodeInsertInfo);
                    newNode.insertInfo = null;
                    return null;
                } else {
                    // if fails, help the current operation
                    // [CHECK]
                    // need to get the latest p.info since CAS doesnt return current value
                    slow_help(p.info);
                }
            }
        }
    }

    private final V fast_putIfAbsent(final K key, final V value) {
        if (key == null || value == null) throw new NullPointerException();
        InternalNode<K, V> newInternal;
        LeafNode<K, V> newSibling, newNode;

        /** SEARCH VARIABLES **/
        InternalNode<K, V> p;
        Info<K, V> pinfo;
        Node<K, V> l;
        /** END SEARCH VARIABLES **/

        newNode = new LeafNode<K, V>(key, value);

        while (true) {

            /** SEARCH **/
            p = root;
            l = p.left;
            while (l.getClass() == InternalNode.class) {
                p = (InternalNode<K, V>) l;
                l = (p.key == null || key.compareTo(p.key) < 0) ? p.left : p.right;
            }
            pinfo = p.info;                             // read pinfo once instead of every iteration
            if (l != p.left && l != p.right) continue;  // then confirm the child link to l is valid
            // (just as if we'd read p's info field before the reference to l)
            /** END SEARCH **/

            LeafNode<K, V> foundLeaf = (LeafNode<K, V>) l;

            if (pinfo != null && pinfo.getClass() == Mark.class) {
                fast_helpMarked(((Mark<K, V>) pinfo).dinfo);
            } else if (key.equals(foundLeaf.key)) {
                return foundLeaf.value; // key already in the tree, no duplicate allowed
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                fast_help(pinfo);
            } else {
                newSibling = new LeafNode<K, V>(foundLeaf.key, foundLeaf.value);
                if (foundLeaf.key == null || key.compareTo(foundLeaf.key) < 0)  // newinternal = max(ret.foundLeaf.key, key);
                    newInternal = new InternalNode<K, V>(foundLeaf.key, newNode, newSibling);
                else
                    newInternal = new InternalNode<K, V>(key, newSibling, newNode);

                final IInfo<K, V> newPInfo = new IInfo<K, V>(foundLeaf, p, newInternal);

                // try to IFlag parent
                if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) {
                    helpInsert(newPInfo);
                    sizeCalculator.fast_updateMetadata(UpdateOperations.OpKind.INSERT, ThreadID.threadID.get());
                    return null;
                } else {
                    // if fails, help the current operation
                    // [CHECK]
                    // need to get the latest p.info since CAS doesnt return current value
                    fast_help(p.info);
                }
            }
        }
    }

    private final V slow_put(final K key, final V value) {
        if (key == null || value == null) throw new NullPointerException();
        InternalNode<K, V> newInternal;
        LeafNode<K, V> newSibling;
        IInfo<K, V> newPInfo;

        /** SEARCH VARIABLES **/
        InternalNode<K, V> p;
        Info<K, V> pinfo;
        Node<K, V> l;
        /** END SEARCH VARIABLES **/
        UpdateInfo newNodeInsertInfo = sizeCalculator.createUpdateInfo(UpdateOperations.OpKind.Separated.INSERT);
        LeafNode<K, V> newNode = new LeafNode<K, V>(key, value, newNodeInsertInfo);

        while (true) {

            /** SEARCH **/
            p = root;
            l = p.left;
            while (l.getClass() == InternalNode.class) {
                p = (InternalNode<K, V>) l;
                l = (p.key == null || key.compareTo(p.key) < 0) ? p.left : p.right;
            }
            pinfo = p.info;                             // read pinfo once instead of every iteration
            if (l != p.left && l != p.right) continue;  // then confirm the child link to l is valid
            // (just as if we'd read p's info field before the reference to l)
            /** END SEARCH **/

            if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                slow_help(pinfo);
            } else {
                LeafNode<K, V> foundLeaf = (LeafNode<K, V>) l;
                if (key.equals(foundLeaf.key)) {
                    // key already in the tree, try to replace the old node with a new node.
                    // In the new node, place the insert info of the insert operation that inserted key to the tree.
                    UpdateInfo insertInfo = foundLeaf.insertInfo;
                    LeafNode<K, V> newNodeReplacingExisting = new LeafNode<K, V>(key, value, insertInfo); // a new node with the same key but the new value
                    newPInfo = new IInfo<K, V>(foundLeaf, p, newNodeReplacingExisting);
                    // try to IFlag parent
                    if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) { // iflag step
                        helpInsert(newPInfo);
                        if (insertInfo != null) {
                            sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                            newNodeReplacingExisting.insertInfo = null;
                        }
                        return foundLeaf.value;
                    }
                } else {
                    // key is not in the tree, try to replace a leaf with a small subtree
                    newSibling = new LeafNode<K, V>(foundLeaf);
                    if (foundLeaf.key == null || key.compareTo(foundLeaf.key) < 0) // newinternal = max(ret.foundLeaf.key, key);
                    {
                        newInternal = new InternalNode<K, V>(foundLeaf.key, newNode, newSibling);
                    } else {
                        newInternal = new InternalNode<K, V>(key, newSibling, newNode);
                    }
                    newPInfo = new IInfo<K, V>(foundLeaf, p, newInternal);
                    // try to IFlag parent
                    if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) { // iflag step
                        helpInsert(newPInfo);
                        sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, newNodeInsertInfo);
                        newNode.insertInfo = null;
                        return null;
                    }
                }

                // if fails to iflag node, help the current operation
                // need to get the latest p.info since CAS doesnt return current value
                slow_help(p.info);
            }
        }
    }

    private final V fast_put(final K key, final V value) {
        if (key == null || value == null) throw new NullPointerException();
        InternalNode<K, V> newInternal;
        LeafNode<K, V> newSibling, newNode;
        IInfo<K, V> newPInfo;
        V result;

        /** SEARCH VARIABLES **/
        InternalNode<K, V> p;
        Info<K, V> pinfo;
        Node<K, V> l;
        /** END SEARCH VARIABLES **/
        newNode = new LeafNode<K, V>(key, value);

        while (true) {

            /** SEARCH **/
            p = root;
            l = p.left;
            while (l.getClass() == InternalNode.class) {
                p = (InternalNode<K, V>) l;
                l = (p.key == null || key.compareTo(p.key) < 0) ? p.left : p.right;
            }
            pinfo = p.info;                             // read pinfo once instead of every iteration
            if (l != p.left && l != p.right) continue;  // then confirm the child link to l is valid
            // (just as if we'd read p's info field before the reference to l)
            /** END SEARCH **/

            if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                fast_help(pinfo);
            } else {
                LeafNode<K, V> foundLeaf = (LeafNode<K, V>) l;

                if (key.equals(foundLeaf.key)) {
                    // key already in the tree, try to replace the old node with new node
                    newPInfo = new IInfo<K, V>(foundLeaf, p, newNode);
                    result = foundLeaf.value;
                } else {
                    // key is not in the tree, try to replace a leaf with a small subtree
                    newSibling = new LeafNode<K, V>(foundLeaf.key, foundLeaf.value);
                    if (foundLeaf.key == null || key.compareTo(foundLeaf.key) < 0) // newinternal = max(ret.foundLeaf.key, key);
                    {
                        newInternal = new InternalNode<K, V>(foundLeaf.key, newNode, newSibling);
                    } else {
                        newInternal = new InternalNode<K, V>(key, newSibling, newNode);
                    }

                    newPInfo = new IInfo<K, V>(foundLeaf, p, newInternal);
                    result = null;
                }

                // try to IFlag parent
                if (infoUpdater.compareAndSet(p, pinfo, newPInfo)) {
                    helpInsert(newPInfo);
                    sizeCalculator.fast_updateMetadata(UpdateOperations.OpKind.INSERT, ThreadID.threadID.get());
                    return result;
                } else {
                    // if fails, help the current operation
                    // need to get the latest p.info since CAS doesnt return current value
                    fast_help(p.info);
                }
            }
        }
    }

    private final V slow_remove(final K key) {
        /** SEARCH VARIABLES **/
        InternalNode<K, V> gp;
        Info<K, V> gpinfo;
        InternalNode<K, V> p;
        Info<K, V> pinfo;
        Node<K, V> l;
        /** END SEARCH VARIABLES **/

        while (true) {

            /** SEARCH **/
            gp = null;
            gpinfo = null;
            p = root;
            pinfo = p.info;
            l = p.left;
            while (l.getClass() == InternalNode.class) {
                gp = p;
                p = (InternalNode<K, V>) l;
                l = (p.key == null || key.compareTo(p.key) < 0) ? p.left : p.right;
            }
            // note: gp can be null here, because clearly the root.left.left == null
            //       when the tree is empty. however, in this case, l.key will be null,
            //       and the function will return null, so this does not pose a problem.
            if (gp != null) {
                gpinfo = gp.info;                               // - read gpinfo once instead of every iteration
                if (p != gp.left && p != gp.right) continue;    //   then confirm the child link to p is valid
                pinfo = p.info;                                 //   (just as if we'd read gp's info field before the reference to p)
                if (l != p.left && l != p.right) continue;      // - do the same for pinfo and l
            }
            /** END SEARCH **/

            if (!key.equals(l.key)) return null;
            if (!(gpinfo == null || gpinfo.getClass() == Clean.class)) {
                slow_help(gpinfo);
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                slow_help(pinfo);
            } else {
                LeafNode<K, V> foundLeaf = (LeafNode<K, V>) l;

                UpdateInfo insertInfo = foundLeaf.insertInfo;
                if (insertInfo != null) {
                    sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.INSERT, insertInfo);
                    foundLeaf.insertInfo = null;
                }

                // try to DFlag grandparent
                int tid = ThreadID.threadID.get();
                final DInfo<K, V> newGPInfo = new DInfo<K, V>(foundLeaf, p, gp, pinfo,
                        tid, sizeCalculator.getThreadUpdateCounter(tid, UpdateOperations.OpKind.Separated.REMOVE) + 1);
                if (infoUpdater.compareAndSet(gp, gpinfo, newGPInfo)) { // dflag step
                    if (slow_helpDelete(newGPInfo)) return foundLeaf.value;
                } else {
                    // if fails, help grandparent with its latest info value
                    slow_help(gp.info);
                }
            }
        }
    }

    private final V fast_remove(final K key) {
        /** SEARCH VARIABLES **/
        InternalNode<K, V> gp;
        Info<K, V> gpinfo;
        InternalNode<K, V> p;
        Info<K, V> pinfo;
        Node<K, V> l;
        /** END SEARCH VARIABLES **/

        while (true) {

            /** SEARCH **/
            gp = null;
            gpinfo = null;
            p = root;
            pinfo = p.info;
            l = p.left;
            while (l.getClass() == InternalNode.class) {
                gp = p;
                p = (InternalNode<K, V>) l;
                l = (p.key == null || key.compareTo(p.key) < 0) ? p.left : p.right;
            }
            // note: gp can be null here, because clearly the root.left.left == null
            //       when the tree is empty. however, in this case, l.key will be null,
            //       and the function will return null, so this does not pose a problem.
            if (gp != null) {
                gpinfo = gp.info;                               // - read gpinfo once instead of every iteration
                if (p != gp.left && p != gp.right) continue;    //   then confirm the child link to p is valid
                pinfo = p.info;                                 //   (just as if we'd read gp's info field before the reference to p)
                if (l != p.left && l != p.right) continue;      // - do the same for pinfo and l
            }
            /** END SEARCH **/

            if (!key.equals(l.key)) return null;
            if (!(gpinfo == null || gpinfo.getClass() == Clean.class)) {
                fast_help(gpinfo);
            } else if (!(pinfo == null || pinfo.getClass() == Clean.class)) {
                fast_help(pinfo);
            } else {
                LeafNode<K, V> foundLeaf = (LeafNode<K, V>) l;
                // try to DFlag grandparent
                final DInfo<K, V> newGPInfo = new DInfo<K, V>(foundLeaf, p, gp, pinfo, 0, 0);

                if (infoUpdater.compareAndSet(gp, gpinfo, newGPInfo)) {
                    if (fast_helpDelete(newGPInfo)) {
                        sizeCalculator.fast_updateMetadata(UpdateOperations.OpKind.REMOVE, ThreadID.threadID.get());
                        return foundLeaf.value;
                    }
                } else {
                    // if fails, help grandparent with its latest info value
                    fast_help(gp.info);
                }
            }
        }
    }

    private void helpInsert(final IInfo<K, V> info) {
        (info.p.left == info.l ? leftUpdater : rightUpdater).compareAndSet(info.p, info.l, info.lReplacingNode); // ichild step
        infoUpdater.compareAndSet(info.p, info, new Clean()); // iunflag step
    }

    private boolean slow_helpDelete(final DInfo<K, V> info) {
        final boolean result;

        result = infoUpdater.compareAndSet(info.p, info.pinfo, new Mark<K, V>(info)); // mark step
        final Info<K, V> currentPInfo = info.p.info;
        if (result || (currentPInfo.getClass() == Mark.class && ((Mark<K, V>) currentPInfo).dinfo == info)) {
            // CAS succeeded or somebody else already helped
            slow_helpMarked(info);
            return true;
        } else {
            slow_help(currentPInfo);
            infoUpdater.compareAndSet(info.gp, info, new Clean()); // backtrack step
            return false;
        }
    }

    private boolean fast_helpDelete(final DInfo<K, V> info) {
        final boolean result;

        result = infoUpdater.compareAndSet(info.p, info.pinfo, new Mark<K, V>(info)); // mark step
        final Info<K, V> currentPInfo = info.p.info;
        if (result || (currentPInfo.getClass() == Mark.class && ((Mark<K, V>) currentPInfo).dinfo == info)) {
            // CAS succeeded or somebody else already helped
            fast_helpMarked(info);
            return true;
        } else {
            fast_help(currentPInfo);
            infoUpdater.compareAndSet(info.gp, info, new Clean()); // backtrack step
            return false;
        }
    }

    private void slow_help(final Info<K, V> info) {
        if (info.getClass() == IInfo.class) helpInsert((IInfo<K, V>) info);
        else if (info.getClass() == DInfo.class) slow_helpDelete((DInfo<K, V>) info);
        else if (info.getClass() == Mark.class) slow_helpMarked(((Mark<K, V>) info).dinfo);
    }

    private void fast_help(final Info<K, V> info) {
        if (info.getClass() == IInfo.class) helpInsert((IInfo<K, V>) info);
        else if (info.getClass() == DInfo.class) fast_helpDelete((DInfo<K, V>) info);
        else if (info.getClass() == Mark.class) fast_helpMarked(((Mark<K, V>) info).dinfo);
    }

    private void slow_helpMarked(final DInfo<K, V> info) {
        sizeCalculator.updateMetadata(UpdateOperations.OpKind.Separated.REMOVE, info);
        final Node<K, V> other = (info.p.right == info.l) ? info.p.left : info.p.right;
        (info.gp.left == info.p ? leftUpdater : rightUpdater).compareAndSet(info.gp, info.p, other); // dchild step
        infoUpdater.compareAndSet(info.gp, info, new Clean()); // dunflag step
    }

    private void fast_helpMarked(final DInfo<K, V> info) {
        final Node<K, V> other = (info.p.right == info.l) ? info.p.left : info.p.right;
        (info.gp.left == info.p ? leftUpdater : rightUpdater).compareAndSet(info.gp, info.p, other);
        infoUpdater.compareAndSet(info.gp, info, new Clean());
    }

    /**
     * Checks If a thread should operate in the slow path or fast path by parity of the size phase.
     */
    private boolean useFastPath(long sizePhase) {
        return (sizePhase & 1) == 0;
    }

    /**
     * DEBUG CODE (FOR TESTBED)
     */

    public long getSumOfKeys() {
        return getSumOfKeys(root);
    }

    // Not accurate if concurrent with remove, since considers nodes that are not yet unlinked as in the tree even if their removal is already linearized
    private long getSumOfKeys(Node node) {
        long sum = 0;
        if (node.getClass() == LeafNode.class)
            sum += node.key != null ? (int) (Integer) node.key : 0;
        else
            sum += getSumOfKeys(((InternalNode<K, V>) node).left) + getSumOfKeys(((InternalNode<K, V>) node).right);
        return sum;
    }
}