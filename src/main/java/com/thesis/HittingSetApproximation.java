package com.thesis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HittingSetApproximation<T> {
	private List<Set<T>> sets;
    public HittingSetApproximation(List<Set<T>> sets) {
    	this.sets=sets;
    }
    
    public Set<T> Execute(){
    	Set<T> solution = new HashSet<T>();
    	HashMap<T, HashSet<Integer>> elementIn = new HashMap<T, HashSet<Integer>>();
    	for(int i=0; i<sets.size();i++) {
    		Set<T> s = sets.get(i);
    		for(T x : s) {
    			HashSet<Integer> c=elementIn.getOrDefault(x, new HashSet<Integer>());
    			c.add(i);
    			elementIn.put(x, c);
    		}
    	}
        for(int i=0;i<sets.size();i++) {
        	Set<T> s = sets.get(i);
        	int maximum =0;
        	T chosen = null;
        	for(T x:s) {
        		int size =elementIn.get(x).size();
        		if(size>maximum) {
        			maximum = size;
        			chosen  = x;
        		}
                elementIn.get(x).remove(i);
        	}
        	solution.add(chosen);
        	
        }
    	return solution;
    }
}
