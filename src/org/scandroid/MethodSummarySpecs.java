/**
 * 
 */
package org.scandroid;

import java.io.UTFDataFormatException;
import java.util.List;

import com.google.common.collect.Lists;
import com.ibm.wala.types.MethodReference;

import spec.EntryArgSinkSpec;
import spec.EntryArgSourceSpec;
import spec.EntryRetSinkSpec;
import spec.ISpecs;
import spec.MethodNamePattern;
import spec.SinkSpec;
import spec.SourceSpec;

/**
 * @author creswick
 *
 */
public class MethodSummarySpecs implements ISpecs {

	private final MethodReference methodRef;

	public MethodSummarySpecs(MethodReference methodRef) {
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

	private List<SinkSpec> getSinks(MethodReference methodRef) throws UTFDataFormatException {
		List<SinkSpec> sinks = Lists.newArrayList();
		
		//
		// Add the args as EntryArgSinkSpecs:
		// 
		String className = methodRef.getDeclaringClass().getName().toUnicodeString();
		String methodName = methodRef.getName().toUnicodeString();
		String descriptor = methodRef.getDescriptor().toUnicodeString();
		MethodNamePattern pattern = new MethodNamePattern(className, methodName, descriptor);
		sinks.add(new EntryArgSinkSpec(pattern, new int[] { }));
		
		//
		// Add the return value as a EntryRetSinkSpec
		//
		sinks.add(new EntryRetSinkSpec(pattern));
		
		return sinks;
	}

	private List<SourceSpec> getSources(MethodReference methodRef) throws UTFDataFormatException {
		List<SourceSpec> sources = Lists.newArrayList();
		
		//
		// Add the args as EntryArgSourceSpecs:
		// 
		String className = methodRef.getDeclaringClass().getName().toUnicodeString();
		String methodName = methodRef.getName().toUnicodeString();
		String descriptor = methodRef.getDescriptor().toUnicodeString();
		MethodNamePattern pattern = new MethodNamePattern(className, methodName, descriptor);

		// TODO make sure the empty array does tag all params:
		sources.add(new EntryArgSourceSpec(pattern, new int[] { }));
		
		return sources;
	}
}
