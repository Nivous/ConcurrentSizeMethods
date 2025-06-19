package measurements.support;

/**
 * Implementation of the paper "A Study of Synchronization Methods for Concurrent Size"
 * by Hen Kas-Sharir, Gal Sela, and Erez Petrank.
 *
 * This file implements the thread registration and deregistration mechanism
 * presented in the paper, which is used to manage thread IDs in a concurrent environment.
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

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadID {
  public static final ThreadLocal<Integer> threadID = new ThreadLocal<Integer>();
  public static final int MAX_THREADS = 128;
  static PriorityBlockingQueue<Integer> pQueue = new PriorityBlockingQueue<Integer>(MAX_THREADS);
  public static AtomicInteger nextId = new AtomicInteger(0);

  public static void register() {
    if (threadID.get() != null)
      throw new RuntimeException("Already registered");
    Integer tid = pQueue.poll();
    if (tid == null) {
      int id = nextId.getAndIncrement();
      if (id >= MAX_THREADS) {
        throw new RuntimeException("Too many threads");
      }
      threadID.set(id);
    } else {
      threadID.set(tid);
    }
  }

  public static void deregister() {
    Integer tid = threadID.get();
    if (tid == null) {
      System.out.println("Thread ID is null");
      return;
    }
    pQueue.add(tid);
    threadID.set(null); // this can be replaced with threadID.remove(); to 
                        // avoid memory leaks in case of long-running threads 
  }
}