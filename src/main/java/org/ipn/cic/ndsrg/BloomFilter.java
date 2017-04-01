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

/*
 *     Copyright (C) 2017  Jairo Sanchez
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package org.ipn.cic.ndsrg;


import com.bitlove.fnv.FNV;
import ie.ucd.murmur.MurmurHash;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Implementation of a proposed Bloom filter variant. This idea is based on a Counting Bloom Filter but does not support
 * element deletion. It has a new set of operations <code>degradation, join</code>
 * @param <T> The type of elements that this Bloom filter will hold
 */
public class BloomFilter<T> implements Serializable{

    private static volatile long SEED = 461943701L;
    private static final double  DEFAULT_FPP = 0.03;
    private static final int     DEFAULT_MAX_COUNT = 64;

    private Integer expectedNumberOfElements;

    private Integer counters;         //m
    private Integer hashFunctions;    //k
    private Integer maxCounterValue;  //c

    private int[] array;


    /**
     * Main constructor. Constructs an empty Bloom filter.
     * @param n The expected number of elements that this filter will hold
     * @param m The number of counters
     */
    public BloomFilter(int n, int m){
        this(m, (int)Math.ceil( m * Math.log(2) / n), DEFAULT_MAX_COUNT);
    }

    public BloomFilter(double falsePositiveProb, int n){
        this((int)Math.ceil(-n*Math.log(falsePositiveProb)/Math.pow(Math.log(2),2)), //m= ceil( -n*ln(p) / (ln(2))^2)
                (int)Math.ceil(-Math.log(falsePositiveProb) / Math.log(2)), // k = ceil(-log_2(falseProb))
                DEFAULT_MAX_COUNT);
    }

    /**
     * Constructs an empty Bloom filter
     * @param m The number of counters it will hold
     * @param k The number of hash functions used for each element
     * @param c The maximum value for any counter
     */
    public BloomFilter(Integer m , Integer k, Integer c){
        this.array = new int[m];

        this.counters = m;
        this.maxCounterValue = c;
        this.hashFunctions = k;

        //Expected number of elements to achieve a 1% false positive probability
        expectedNumberOfElements = (int)Math.floor( -((double)m/(double)k) * Math.log(1 - Math.pow(0.1, 1/((double)k))));
    }

    /**
     * Constructs an empty Bloom filter based on another Bloom filter. Copies all data from the other filter
     * @param other The external filter to be copied
     */
    public BloomFilter(BloomFilter other){
        this(other.counters, other.hashFunctions, other.maxCounterValue);
        this.array = other.array.clone();
        this.expectedNumberOfElements = other.expectedNumberOfElements;
    }

    /**
     * Adds an element to the Bloom filter representation
     * @param element the element to be added
     */
    public void add(T element){

        List<Integer> indexes = hashesFor(element);
        for(Integer i : indexes){
            this.array[i] = this.maxCounterValue;
        }
    }

    /**
     * Degrades the information contained in this filter stochastically
     */
    public void stochasticDegrade(double dPr){
        Random r = new Random(SEED);

        for(int i=0; i<counters; i++){
            int theThrow = r.nextInt(1000);
            if(((float)theThrow/1000.0) < dPr && this.array[i] > 0){
                this.array[i]--;
            }
        }
    }


    /**
     * Degrades the information of this Bloom filter deterministically
     */
    public void deterministicDegradation(){
        for(int i=0; i<counters; i++){
            if(this.array[i] > 0)
                this.array[i]--;
        }
    }


    /**
     * Joins two Bloom Filters
     * @param bf1 The first Bloom Filter
     * @param bf2 The second Bloom filter
     * @return The resulting Bloom filter or null if the arguments are incompatible
     */
    public static BloomFilter join(BloomFilter bf1, BloomFilter bf2){
        if(bf1.counters != bf2.counters) return null;

        BloomFilter result = new BloomFilter(bf1.counters, bf1.hashFunctions, bf1.maxCounterValue);
        for(int i=0; i<  bf1.counters; i++){
            result.array[i] = bf1.array[i] > bf2.array[i] ? bf1.array[i] : bf2.array[i];
        }
        return result;
    }


    /**
     * Tests if a certain element might be in this Bloom filter (Keep in mind the false positive probability)
     * @param element the element to be tested
     * @return true if the element might be in the filter, false if it's not
     */
    public boolean mightHave(T element){
        List<Integer> indexes = hashesFor(element);
        boolean retvalue = true;
        for(Integer index : indexes){
            retvalue = retvalue && (array[index] != 0);
        }
        return retvalue;
    }

    /**
     * Gets the value of a specific counter
     * @param index The index of the counter
     * @return The current value of the counter
     */
    public Integer counterAt(Integer index) throws IndexOutOfBoundsException{
        if(index < this.counters && index > 0)
            return array[index];
        else
            throw new IndexOutOfBoundsException("The specified counter does not exist on this Bloom filter");
    }

    /**
     * Creates a List with the result of applying the k hash functions over an element
     * @param element The element
     * @return The list with all the results
     */
    public List<Integer> hashesFor(T element){
        List<Integer> hashes = new ArrayList<Integer>();
        for(int i=0; i<this.hashFunctions; i++){
            byte[] data = objectToBytes(element);
            Long h = Long.valueOf((new FNV()).fnv1_32(data).intValue());
            Long g = Long.valueOf(MurmurHash.hash32(data, data.length));
            h = h<0? -h : h;
            g = g<0? -g : g;

            Long ki = (g + i*h) % this.counters;

            hashes.add(ki.intValue());
        }
        return hashes;
    }


    /**
     * Calculates the false positive probability for this Bloom filter
     * @return the false positive probability
     */
    public double falsePositiveProbability(){

        return Math.pow(1-Math.exp(-(double)hashFunctions*(double)expectedNumberOfElements/(double)counters), hashFunctions);
    }

    /**
     * Converts an element into a byte array representation
     * @param object The object to be converted
     * @return The byte array representation of the object
     */
    private byte[] objectToBytes(T object){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        byte[] theBytes = new byte[1];
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(object);
            out.flush();
            theBytes = bos.toByteArray();
            return theBytes;

        }catch (IOException e){
            System.err.println("Error serializing object.");
            System.err.println(e.getMessage());
        }finally {
            try{
                bos.close();
            }catch(IOException e){
                //Ignore the close exception
            }
        }
        return null;
    }

    /**
     * Creates a human readable representation of this object
     * @return The representation
     */
    @Override
    public String toString() {
        return "BloomFilter("+counters+"," +hashFunctions + "," + maxCounterValue+"){" +
                Arrays.toString(array) +
                '}';
    }


    @Override
    public boolean equals(Object obj) {
        if(obj.getClass() != BloomFilter.class) return false;
        BloomFilter other = (BloomFilter)obj;

        if(this.counters != other.counters) return false;
        if(this.hashFunctions != other.hashFunctions) return false;
        if(this.maxCounterValue != other.maxCounterValue) return false;

        for(int i=0; i<counters; i++){
            if(this.array[i] != other.array[i]) return false;
        }


        return true;
    }

    /**
     * Gets the total sum of all counters
     * @return The sum
     */
    public Integer sum(){
        Integer result=0;
        for(int i=0; i<counters; i++)
            result += this.array[i];

        return result;
    }


    public void printHashesFor(T element){
        List<Integer> idx = hashesFor(element);
        String s = "F[";
        for(Integer i : idx){
            s += i.toString() + ",";
        }
        s+="]";
        System.out.println(s);
    }
}