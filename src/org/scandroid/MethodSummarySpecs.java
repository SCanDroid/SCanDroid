/**
 * 
 */
package org.scandroid;

import java.io.UTFDataFormatException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spec.EntryArgSinkSpec;
import spec.EntryArgSourceSpec;
import spec.EntryRetSinkSpec;
import spec.ISpecs;
import spec.MethodNamePattern;
import spec.SinkSpec;
import spec.SourceSpec;

import com.google.common.collect.Lists;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/**
 * @author creswick
 *
 */
public class MethodSummarySpecs implements ISpecs {
	private static final Logger logger = LoggerFactory.getLogger(MethodSummarySpecs.class);

	private final MethodSummary methodRef;

	public MethodSummarySpecs(MethodSummary methodRef) {
		this.methodRef = methodRef;
	}
	
	/* (non-Javadoc)
	 * @see spec.ISpecs#getEntrypointSpecs()
	 */
	@Override
	public MethodNamePattern[] getEntrypointSpecs() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see spec.ISpecs#getSourceSpecs()
	 */
	@Override
	public SourceSpec[] getSourceSpecs() {
		try {
			return getSources(methodRef).toArray(new SourceSpec[] {});
		} catch (UTFDataFormatException e) {
			e.printStackTrace();
		}
		return new SourceSpec[] {};
	}

	/* (non-Javadoc)
	 * @see spec.ISpecs#getSinkSpecs()
	 */
	@Override
	public SinkSpec[] getSinkSpecs() {
		try {
			return getSinks(methodRef).toArray(new SinkSpec[] {});
		} catch (UTFDataFormatException e) {
			e.printStackTrace();
		}
		return new SinkSpec[] {};
	}

	private List<SinkSpec> getSinks(MethodSummary mSummary) throws UTFDataFormatException {
		List<SinkSpec> sinks = Lists.newArrayList();
		
		MethodReference mRef = (MethodReference) mSummary.getMethod();
		
		//
		// Add the args as EntryArgSinkSpecs:
		// 
		String className = mRef.getDeclaringClass().getName().toUnicodeString();
		String methodName = mRef.getName().toUnicodeString();
		String descriptor = mRef.getDescriptor().toUnicodeString();
		MethodNamePattern pattern = new MethodNamePattern(className, methodName, descriptor);
		
		int[] argNums = new int[mSummary.getNumberOfParameters()];
		for (int i = 0; i < argNums.length; i++) {
			argNums[i] = i;
		}
		sinks.add(new EntryArgSinkSpec(pattern, argNums));
		
		TypeReference typeRef = mRef.getReturnType();
		if (! typeRef.equals(TypeReference.Void)) {
			//
			// Add the return value as a EntryRetSinkSpec
			//
			sinks.add(new EntryRetSinkSpec(pattern));
		}
		
		logger.debug("found sinks: " + sinks.toString());
		return sinks;
	}

	private List<SourceSpec> getSources(MethodSummary mSummary) throws UTFDataFormatException {
		List<SourceSpec> sources = Lists.newArrayList();
		MethodReference mRef = (MethodReference) mSummary.getMethod();
		
		//
		// Add the args as EntryArgSourceSpecs:
		// 
		String className = mRef.getDeclaringClass().getName().toUnicodeString();
		String methodName = mRef.getName().toUnicodeString();
		String descriptor = mRef.getDescriptor().toUnicodeString();
		MethodNamePattern pattern = new MethodNamePattern(className, methodName, descriptor);

		int[] argNums = new int[mSummary.getNumberOfParameters()];
		for (int i = 0; i < argNums.length; i++) {
			argNums[i] = i;
		}
		sources.add(new EntryArgSourceSpec(pattern, argNums));
		
		logger.debug("found sources: " + sources.toString());
		return sources;
	}
}
