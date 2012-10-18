package org.scandroid.testing;

public class MutualRecursion {
	// versions of even/odd predicates that return 0 or 1 to stand for false and
	// true in order to have instance key flows
	public int evenp(int x) {
		if (x == 0) {
			return 1;
		} else {
			return oddp(x - 1);
		}			
	}
	
	public int oddp(int x) {
		if (x == 0) {
			return 0;
		} else {
			return evenp(x - 1);
		}
	}
	
	// trickier case: mutual recursion with changing types
	public static class Foo {
		public Bar bar;

		public Foo(Bar bar) {
			this.bar = bar;
		}
	}
	
	public static class Bar {
	    public Foo foo;

		public Bar(Foo foo) {
			this.foo = foo;
		}
	}
	
	public Foo fooize(Bar bar) {
		return new Foo(barize(bar.foo));
	}
	
	public Bar barize(Foo foo) {
		return new Bar(fooize(foo.bar));
	}
}
