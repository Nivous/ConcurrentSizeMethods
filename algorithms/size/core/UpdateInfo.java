package algorithms.size.core;

/**
 * This is an implementation of the paper "Concurrent Size" by Gal Sela and Erez Petrank.
 * <p>
 * Copyright (C) 2022  Gal Sela
 * Contact Gal Sela (sela.galy@gmail.com) with any questions or comments.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

public record UpdateInfo(int tid, long counter) implements UpdateInfoHolder {
    @Override
    public int getTid() {
        return tid;
    }

    @Override
    public long getCounter() {
        return counter;
    }
}
