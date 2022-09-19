package net.floodlightcontroller.dynamicservice;

import java.util.Optional;
import java.util.TreeSet;

/*
 * Class that implement a PoolManager, a data structure where two operations
 * are defined: insertion to insert a number into the structure, extraction
 * that removes and returns a number previously inserted. 
 */
public class PoolManager{
	public class Range implements Comparable<Range>{
		public long start, end;
		public Range(long _start, long _end) { start = _start; end   = _end; }
		public Range(int _start, int _end) { start = (long) _start; end = (long) _end; }
		public boolean contains(long value) { return value >= start && value <= end; }
		public boolean intercept(Range r) {
			if(r.start < this.start) {
				return r.end + 1 >= this.start;
			}else if(r.start > this.start){
				return this.end + 1 >= r.start;
			}
			return true;
		}
		@Override
		public boolean equals(Object o) {
			if(o instanceof Range == false)
				return false;
			Range range = (Range) o;
			return this.start == range.start && this.end == range.end;
		}
		
		@Override
		public int compareTo(Range r) {
			return Long.compare(this.end, r.end);
		}
	};
	
	private TreeSet<Range> freeValues;
	
	public PoolManager() {
		freeValues = new TreeSet<>();
	}
	
	public PoolManager add(int start, int end) {
		Range r = new Range(start, end);
		
		
		Range smaller = freeValues.floor(r);
		while(smaller != null && smaller.intercept(r)) {
			r.start = Math.min(r.start, smaller.start);
			r.end = Math.max(r.end, smaller.end);
			freeValues.remove(smaller);
			smaller = freeValues.floor(r);
		}
		
		Range higher = freeValues.ceiling(r);
		while(higher != null && higher.intercept(r)) {
			r.start = Math.min(r.start, higher.start);
			r.end = Math.max(r.end, higher.end);
			freeValues.remove(higher);
			higher = freeValues.ceiling(r);
		}
		
		freeValues.add(r);
		return this;
	}
	
	public Optional<Long> get() {
		if(freeValues.isEmpty())
			return Optional.ofNullable(null);
		Range r = freeValues.first();
		if(r.start == r.end)
			freeValues.remove(r);
		long value = r.start;
		r.start++;
		
		return Optional.of(value);
	}
	
	public PoolManager add(int element) {
		return this.add(element, element);	
	}
	
	@Override
	public String toString() {
		StringBuilder response = new StringBuilder();
		for(Range r : freeValues)
			response.append("[" + r.start + ", " + r.end + "]");
		return response.toString();
	}
}
