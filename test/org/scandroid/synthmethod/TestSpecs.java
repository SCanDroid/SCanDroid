package org.scandroid.synthmethod;

import java.util.Collection;

import org.scandroid.MethodSummarySpecs;
import org.scandroid.spec.CallArgSinkSpec;
import org.scandroid.spec.EntryArgSourceSpec;
import org.scandroid.spec.ISpecs;
import org.scandroid.spec.MethodNamePattern;
import org.scandroid.spec.SinkSpec;
import org.scandroid.spec.SourceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
}
