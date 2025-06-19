package algorithms.size.core;

/**
 * This is an implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
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

public interface SizeSet<K, V> {
    boolean containsKey(K key);

    V putIfAbsent(K key, V Value);

    V remove(K key);

    int size();

    long getSumOfKeys();
}
