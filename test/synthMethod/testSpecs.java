package synthMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;

import spec.*;

public class testSpecs implements ISpecs {
	
	private SourceSpec[] sources;
	private SinkSpec[] sinks;
	
	public testSpecs(Collection<CGNode> nodes) {
		ArrayList<SourceSpec> sourcesAL = new ArrayList<SourceSpec>();
		ArrayList<SinkSpec> sinksAL = new ArrayList<SinkSpec>();

        for (CGNode node:nodes) {
        	String nodeClass = node.getMethod().getDeclaringClass().getName().toString();
        	String nodeMethod = node.getMethod().getName().toString();
        	MethodNamePattern mnp = new MethodNamePattern(nodeClass, nodeMethod);
        	FieldNamePattern fnp = new FieldNamePattern(nodeClass, nodeMethod);
        	
        	sourcesAL.add(new EntryArgSourceSpec(mnp, new int[] {}));
        	sourcesAL.add(new FieldSourceSpec(fnp));
        	sinksAL.add(new EntryRetSinkSpec(mnp));
        	sinksAL.add(new FieldSinkSpec(fnp));

        }        
        sources = sourcesAL.toArray(new SourceSpec[sourcesAL.size()]);        
        sinks = sourcesAL.toArray(new SinkSpec[sinksAL.size()]);

	}
	
	@Override
	public MethodNamePattern[] getEntrypointSpecs() {
		return null;
	}
	@Override
	public SourceSpec[] getSourceSpecs() {
		return sources;
	}
	@Override
	public SinkSpec[] getSinkSpecs() {
		return sinks;
	}

}
