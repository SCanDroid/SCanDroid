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
package org.scandroid.synthmethod;

import org.scandroid.spec.MethodNamePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.strings.Atom;

public class MethodPredicates {
	private static final Logger logger = LoggerFactory.getLogger(MethodPredicates.class);
	
	@SuppressWarnings("unchecked")
	public static final Predicate<IMethod> every = Predicate.TRUE;

	/**
	 * Select methods with the supplied name.
	 * 
	 * @param name
	 * @return
	 */
	public static Predicate<IMethod> isNamed(final String name) {
		return new Predicate<IMethod>() {
			@Override
			public boolean test(IMethod t) {
				String methodName = atomToStr(t.getName());
				return name.equals(methodName);
			}
		};
	}

	public static Predicate<IMethod> matchesPattern(
			final MethodNamePattern p) {
		
		Predicate<IMethod> nameMatches = isNamed(p.getMemberName());
		
		Predicate<IMethod> classMatches = fromClass(p.getClassName());
		
		return nameMatches.and(classMatches);
	}

	private static Predicate<IMethod> fromClass(final String needleClassName) {
		return new Predicate<IMethod>() {
			@Override
			public boolean test(IMethod t) {
				TypeName typeName = t.getDeclaringClass().getName();
				String testClassName = atomToStr(typeName.getClassName());
				String testPkgName = atomToStr(typeName.getPackage());
				String fullClassName = "L"+testPkgName + "/" + testClassName;
				boolean matchFound = needleClassName.equals(fullClassName);
				
				logger.debug("Comparing class: "+fullClassName+"\n"+
						"with: "+needleClassName);
				logger.debug(" matches? "+matchFound);
				
				return matchFound;
			}
		};
	}

	protected static String atomToStr(Atom atom) {
		return new String(atom.getValArray());
	}
}
