/*
*
* Copyright (c) 2009-2012,
*
*  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
*  Steve Suh    <suhsteve@gmail.com>
*
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice,
* this list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
* this list of conditions and the following disclaimer in the documentation
* and/or other materials provided with the distribution.
*
* 3. The names of the contributors may not be used to endorse or promote
* products derived from this software without specific prior written
* permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*
*
*/
package org.scandroid.testing;

public class InnerClassTest {
	public class FooBar {
		public FooBar() {			
		}
	}
	
	private class Foo {
		public Bar bar;

		public Foo(Bar bar) {
			this.bar = bar;
		}
	}
	
	public class Bar {
	    public Foo foo;

		public Bar(Foo foo) {
			this.foo = foo;
		}
	}
	
	private static class StaticFoo {
		public StaticBar bar;

		public StaticFoo(StaticBar bar) {
			this.bar = bar;
		}
	}
	
	public static class StaticBar {
	    public StaticFoo foo;

		public StaticBar(StaticFoo foo) {
			this.foo = foo;
		}
	}
	
	public void doNothing(FooBar fb) {
		
	}
	
	public static void doNothing2(StaticFoo f, StaticBar b) {
		
	}
	
	public void doNothing3(StaticFoo f) {
		
	}
	
	public Foo createFoo(Bar bar) {
		return new Foo(bar);
	}
	
	public Foo getFoo(Bar bar) {
		return bar.foo;
	}
	
	public Foo createFoofromFoo(Foo foo) {
		return new Foo(foo.bar);
	}
	
	public Foo returnParam(Foo foo) {
		return foo;
	}

	
	public Bar createBar(Foo foo) {
		return new Bar(foo);
	}
	
	public Bar getBar(Foo foo) {
		return foo.bar;
	}
	
	public Bar returnParam(Bar bar) {
		return bar;
	}
	
	public Bar createBarfromBar(Bar bar) {
		return new Bar(bar.foo);
	}
}
