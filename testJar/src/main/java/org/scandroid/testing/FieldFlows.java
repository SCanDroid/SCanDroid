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
	
	public int throughField(String x) {
		Pair p = new Pair(null,null);
		p.fst = x.length();
		int ret = ((Integer)p.fst).intValue();
		return ret;
	}
}
