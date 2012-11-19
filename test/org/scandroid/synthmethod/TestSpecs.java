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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.scandroid.MethodSummarySpecs;
import org.scandroid.spec.CallArgSinkSpec;
import org.scandroid.spec.EntryArgSourceSpec;
import org.scandroid.spec.ISpecs;
import org.scandroid.spec.MethodNamePattern;
import org.scandroid.spec.SinkSpec;
import org.scandroid.spec.SourceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.strings.StringStuff;

public class TestSpecs implements ISpecs {
	private static final Logger logger = LoggerFactory
			.getLogger(TestSpecs.class);

	@Override
	public MethodNamePattern[] getEntrypointSpecs() {
		return null;
	}

	@Override
	public SourceSpec[] getSourceSpecs() {
		return new SourceSpec[] { new EntryArgSourceSpec(new MethodNamePattern(
				"Lorg/scandroid/testing/App", "main"), new int[] { 0 }) };
	}

	@Override
	public SinkSpec[] getSinkSpecs() {
		return new SinkSpec[] { new CallArgSinkSpec(new MethodNamePattern(
				"Lorg/scandroid/testing/SourceSink", "sink"), new int[] { 0 }) };
	}

	public static ISpecs specsFromDescriptor(ClassHierarchy cha,
			String methodDescriptor) {
		MethodReference methodRef = StringStuff
				.makeMethodReference(methodDescriptor);
		Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);
		if (entryMethods.size() > 1) {
			logger.error("More than one imethod found for: " + methodRef);
		} else if (entryMethods.size() == 0) {
			logger.error("No method found for: " + methodRef);
			throw new IllegalArgumentException();
		}
		IMethod imethod = entryMethods.iterator().next();
		final MethodSummary methodSummary = new MethodSummary(methodRef);
		methodSummary.setStatic(imethod.isStatic());
		final MethodSummarySpecs methodSummarySpecs = new MethodSummarySpecs(
				methodSummary);
		return methodSummarySpecs;
	}

	/**
	 * Combine two specs objects.
	 * 
	 * @param s1
	 * @param s2
	 * @return
	 */
	public static ISpecs combine(final ISpecs s1, final ISpecs s2) {
		return new ISpecs() {
			@Override
			public SourceSpec[] getSourceSpecs() {
				SourceSpec[] s1Sources = s1.getSourceSpecs();
				SourceSpec[] s2Sources = s2.getSourceSpecs();
				
				return concat(s1Sources, s2Sources);
			}
			
			@Override
			public SinkSpec[] getSinkSpecs() {
				return concat(s1.getSinkSpecs(), s2.getSinkSpecs());
			}
			
			@Override
			public MethodNamePattern[] getEntrypointSpecs() {
				return concat(s1.getEntrypointSpecs(), s2.getEntrypointSpecs());
			}

			@SuppressWarnings("unchecked")
			private <T> T[] concat(final T[] a, final T[] b) {
				if (null == a) {
					return b;
				}
				if (null == b) {
					return a;
				}
				
				T[] newArray = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
				System.arraycopy(a, 0, newArray, 0, a.length);
				System.arraycopy(b, 0, newArray, a.length, b.length);
				return newArray;
			}
		};
	}
}
