package org.scandroid.testing;

public class DeepFields {
	public static class Foo {
		public Bar bar;

		public Foo(Bar bar) {
			this.bar = bar;
		}
	}
	
	public static class Bar {
		public Object baz;

		public Bar(Object baz) {
			this.baz = baz;
		}
		
	}

	public static void test(Foo foo, Object baz) {
		foo.bar.baz = baz;
	}
}
