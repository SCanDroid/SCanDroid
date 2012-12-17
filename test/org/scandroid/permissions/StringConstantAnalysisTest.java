package org.scandroid.permissions;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.scandroid.synthmethod.DefaultSCanDroidOptions;
import org.scandroid.util.AndroidAnalysisContext;
import org.scandroid.util.CGAnalysisContext;
import org.scandroid.util.IEntryPointSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.shrike.ShrikeUtil;
import com.ibm.wala.util.strings.StringStuff;

@RunWith(Parameterized.class)
public class StringConstantAnalysisTest {
	private static final Logger logger = LoggerFactory.getLogger(StringConstantAnalysisTest.class);
	private static final String TEST_DATA_DIR = "data/testdata/";
	private static final String TEST_JAR = TEST_DATA_DIR
			+ "testJar-1.0-SNAPSHOT.";

	@Parameters(name = "{0}")
	public static Collection<Object[]> setup() throws Throwable {
		final Collection<Object[]> params = Lists.newArrayList();
		params.add(new Object[] { "jar" });
		params.add(new Object[] { "dex" });
		return params;
	}

	private String testJar;

	public StringConstantAnalysisTest(String whichJar) {
		this.testJar = TEST_JAR + whichJar;
	}

	@Test
	public void testIKTypes() throws Throwable {
		AndroidAnalysisContext analysisContext = new AndroidAnalysisContext(
				new DefaultSCanDroidOptions() {
					@Override
					public URI getClasspath() {
						return new File(testJar).toURI();
					}

					@Override
					public boolean stdoutCG() {
						return false;
					}
				});
		final MethodReference method = StringStuff
				.makeMethodReference("org.scandroid.testing.StringConstants.returnFoo()Ljava/lang/String;");
		CGAnalysisContext<IExplodedBasicBlock> ctx = new CGAnalysisContext<IExplodedBasicBlock>(
				analysisContext, new IEntryPointSpecifier() {
					@Override
					public List<Entrypoint> specify(
							AndroidAnalysisContext analysisContext) {
						final Entrypoint entrypoint = new DefaultEntrypoint(
								method, analysisContext.getClassHierarchy());
						final List<Entrypoint> entrypoints = Lists
								.newArrayList(entrypoint);
						return entrypoints;
					}
				});		
		for (InstanceKey ik : ctx.pa.getInstanceKeys()) {
			logger.debug("{}: {}", ik.getClass(), ik);		
		}
	}
	
	@Test
	public void testChangeInvoke() throws Throwable {
		AndroidAnalysisContext analysisContext = new AndroidAnalysisContext(
				new DefaultSCanDroidOptions() {
					@Override
					public URI getClasspath() {
						return new File(testJar).toURI();
					}

					@Override
					public boolean stdoutCG() {
						return true;
					}
				});		
		final MethodReference method = StringStuff
				.makeMethodReference("org.scandroid.testing.StringConstants.returnSomething()Ljava/lang/String;");
		final MethodReference newTarget = StringStuff
				.makeMethodReference("org.scandroid.testing.StringConstants.returnBar()Ljava/lang/String;");
		
		CGAnalysisContext<IExplodedBasicBlock> ctx = new CGAnalysisContext<IExplodedBasicBlock>(
				analysisContext, new IEntryPointSpecifier() {
					@Override
					public List<Entrypoint> specify(
							AndroidAnalysisContext analysisContext) {
						final Entrypoint entrypoint = new DefaultEntrypoint(
								method, analysisContext.getClassHierarchy());
						final List<Entrypoint> entrypoints = Lists
								.newArrayList(entrypoint);
						return entrypoints;
					}
				});
		
		IMethod iMethod = analysisContext.getClassHierarchy().resolveMethod(method);
		CGNode node = ctx.cg.getNode(iMethod, Everywhere.EVERYWHERE);
		IR ir = node.getIR();
		MethodSummary newMethod = new MethodSummary(method);
		newMethod.setStatic(iMethod.isStatic());
		Iterator<SSAInstruction> it = ir.iterateAllInstructions();
		while (it.hasNext()) {
			SSAInstruction inst = it.next();
			if (inst instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction inv = (SSAInvokeInstruction) inst;
				int params[] = new int[inv.getNumberOfUses()];
				for (int i = 0; i < params.length; i++) {
					params[i] = inv.getUse(i);
				}					
				final CallSiteReference callSite = inv.getCallSite();
				final CallSiteReference newCallSite = CallSiteReference.make(callSite.getProgramCounter(), newTarget, callSite.getInvocationCode());
				inst = Language.JAVA.instructionFactory().InvokeInstruction(inv.getDef(), params, inv.getException(), newCallSite);
			}
			newMethod.addStatement(inst);
		}
		logger.debug("original SSA: {}", Arrays.toString(ir.getInstructions()));
		logger.debug("new SSA: {}", Arrays.toString(newMethod.getStatements()));
	}

}
