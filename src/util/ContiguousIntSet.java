/**
 * 
 */
package util;

import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.MutableSparseIntSet;

/**
 * @author creswick
 *
 */
public class ContiguousIntSet implements IntSet {

	private final int max;
	private final int min;

	/**
	 * Creates an int set that contains all the ints between min and max, 
	 * including min, excluding max.
	 * 
	 * Creates an empty set if max is less than min.
	 * 
	 * In otherwords, the range: @code [min max) @code
	 * 
	 * @param min
	 * @param max
	 */
	public ContiguousIntSet(int min, int max) {
		if (max < min) {
			this.max = 0;
			this.min = 0;
		} else {
			this.min = min;
			this.max = max;
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#contains(int)
	 */
	@Override
	public boolean contains(int i) {
		return min <= i && i < max;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#containsAny(com.ibm.wala.util.intset.IntSet)
	 */
	@Override
	public boolean containsAny(IntSet set) {
		IntIterator itr = set.intIterator();
		
		while (itr.hasNext()) {
			if (contains(itr.next())){
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#intersection(com.ibm.wala.util.intset.IntSet)
	 */
	@Override
	public IntSet intersection(IntSet that) {
        MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
        
        IntIterator itr = that.intIterator();
        while (itr.hasNext()) {
        	int value = itr.next();
			if (this.contains(value)) {
				set.add(value);
			}
        }
        return set;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#union(com.ibm.wala.util.intset.IntSet)
	 */
	@Override
	public IntSet union(IntSet that) {
		MutableSparseIntSet set = MutableSparseIntSet.make(this);
        
        IntIterator itr = that.intIterator();
        while (itr.hasNext()) {
   		   set.add(itr.next());
        }
        return set;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return 0 == size();
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#size()
	 */
	@Override
	public int size() {
		return max - min;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#intIterator()
	 */
	@Override
	public IntIterator intIterator() {
		return new IntIterator() {
			int current = min;
			
			@Override
			public int next() {
				int val = current;
				current++;
				return val;
			}
			
			@Override
			public boolean hasNext() {
				return current < max;
			}
		};
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#foreach(com.ibm.wala.util.intset.IntSetAction)
	 */
	@Override
	public void foreach(IntSetAction action) {
		IntIterator itr = intIterator();
		while (itr.hasNext()) {
			action.act(itr.next());
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#foreachExcluding(com.ibm.wala.util.intset.IntSet, com.ibm.wala.util.intset.IntSetAction)
	 */
	@Override
	public void foreachExcluding(IntSet X, IntSetAction action) {
		IntIterator itr = intIterator();
		while (itr.hasNext()) {
			int value = itr.next();
			if (! X.contains(value)) {
				action.act(value);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#max()
	 */
	@Override
	public int max() {
		return max;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#sameValue(com.ibm.wala.util.intset.IntSet)
	 */
	@Override
	public boolean sameValue(IntSet that) {
		int intSize = this.intersection(that).size();
		int uniSize = this.union(that).size();
		
		return intSize == uniSize;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.util.intset.IntSet#isSubset(com.ibm.wala.util.intset.IntSet)
	 */
	@Override
	public boolean isSubset(IntSet that) {
		return this.intersection(that).size() == this.size();
	}

}
