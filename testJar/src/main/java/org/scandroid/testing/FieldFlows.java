package org.scandroid.testing;

public class FieldFlows {
	public static class X {}
	public static class Y {}
	
	public static class Pair {
		Object fst;
		Object snd;
		
		public Pair(Object fst, Object snd) {
			this.fst = fst;
			this.snd = snd;
		}			
	}

	public static Object dropFstStatic(Pair p) {
		return p.snd;
	}
	
	public static Object dropSndStatic(Pair p) {
		return p.fst;
	}
	
	public static Pair mkPairStatic(X x, Y y) {
		return new Pair(x, y);
	}
	
	public static Pair swapStatic(Pair p) {
		Object tmp = p.fst;
		p.fst = p.snd;
		p.snd = tmp;
		return p;
	}
	
	public Object dropFst(Pair p) {
		return p.snd;
	}
	
	public Object dropSnd(Pair p) {
		return p.fst;
	}
	
	public Pair mkPair(X x, Y y) {
		return new Pair(x, y);
	}
	
	public Pair swap(Pair p) {
		Object tmp = p.fst;
		p.fst = p.snd;
		p.snd = tmp;
		return p;
	}
	
	public Object throughField(Object x) {
		Pair p = new Pair(null,null);
		p.fst = x;
		Object ret = p.fst;
		return ret;
	}
}
