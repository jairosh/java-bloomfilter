/*
 * Copyright (C) 2017  Jairo Sanchez
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.ipn.cic.ndsrg;


import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BloomFilterTest {

    private BloomFilter<String> a;
    private BloomFilter<String> b;
    private BloomFilter<String> c;

    @Before
    public void setup(){
        this.a = new BloomFilter<String>(40, 4, 32);
        this.b = new BloomFilter<String>(40, 4, 32);
        this.c = new BloomFilter<String>(40, 4, 32);

        a.add("Test");

        b.add("Test");
        b.add("ther");
        b.add("foobar");
        b.add("foo");
        b.add("bar");
    }

    @Test
    public void test_membership(){
        String test = "Test";
        assertTrue("a does not have the item", a.mightHave("Test"));
        assertTrue("b does not have the item", b.mightHave(test));


        assertTrue("a does have \"other\"", !a.mightHave("other"));

        //There must be a collision in this filter
        test = "other";
        assertTrue("b does have \"other\"", b.mightHave(test));
    }


    @Test
    public void test_hashes_for(){
        Integer[] calculated_hashes = {21, 8, 35, 22}; //The hash representation for String "Test"
        List<Integer> hashes = a.hashesFor("Test");
        String msg = "Calculated hash value not found in BloomFilter. Filter: " + a.toString() ;
        assertTrue(msg, hashes.containsAll(Arrays.asList(calculated_hashes)));
    }

    @Test
    public void test_degradation(){
        BloomFilter<String> copy = new BloomFilter<String>(a);
        copy.stochasticDegrade(0.5f);
        assertTrue("There's no stochastic degradation in the filter", copy.sum() < a.sum());

        copy = new BloomFilter<String>(a);
        copy.deterministicDegradation();
        assertTrue("There's no degradation in the filter", copy.sum() < a.sum());
    }

    @Test
    public void test_constructor(){
        BloomFilter<String> copy = new BloomFilter<String>(a);
        assertEquals("Copy constructor does not produce a copy", copy, a);
        assertTrue("Copy constructor does not produce a copy (.equals)", a.equals(copy));
    }


    @Test
    public void test_join(){
        BloomFilter<String> deg = new BloomFilter<String>(a);
        deg.deterministicDegradation();
        assertTrue("Bloom filter copy it's not degraded", deg.sum() < a.sum());

        BloomFilter<String> joined = BloomFilter.join(a, deg);
        assertTrue("Incorrect Bloom filter join" + joined.toString(), joined.equals(a));
    }
}
