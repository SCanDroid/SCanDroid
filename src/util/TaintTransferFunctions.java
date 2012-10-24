package util;

import java.util.List;

import com.google.common.collect.Lists;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;

public class TaintTransferFunctions <E extends ISSABasicBlock> implements
        IFlowFunctionMap<BasicBlockInContext<E>> {

    private final IFDSTaintDomain<E> domain;
    private final ISupergraph<BasicBlockInContext<E>,CGNode> graph;
    private final PointerAnalysis pa;

    public TaintTransferFunctions(IFDSTaintDomain<E> domain,
            ISupergraph<BasicBlockInContext<E>, CGNode> graph, 
            PointerAnalysis pa) {
        this.domain = domain;
        this.graph = graph;
        this.pa = pa;
    }

    private final static class UseDefPair
    {
        private final CodeElement use;
        private final CodeElement def;
        public UseDefPair(CodeElement use, CodeElement def) {
            this.use = use;
            this.def = def;
        }
        public CodeElement getUse() {
            return use;
        }
        public CodeElement getDef() {
            return def;
        }
    }

    private final class DefUse implements IUnaryFlowFunction {
        private final List<UseDefPair> useToDefList = Lists.newArrayList();

        private final BasicBlockInContext<E> bb;

        public DefUse(final BasicBlockInContext<E> inBlock) {    
            this.bb = inBlock;
        }
        
        @Override
        public IntSet getTargets(int d) {
            MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
            
            DomainElement de = domain.getMappedObject(d);
            if(d == 0) {
                /* Here we list what facts we add. All taints affecting uses of
                 * this instruction also affect its definitions.
                 */
                set.add(0);
            } else {
                /* Here we list what facts we pass through. If a fact was true
                 * before executing this instruction, it'll be true after,
                 * unless we created a new definition of its associated
                 * CodeElement.
                 */
            	
            	// see if D is still true; if so, pass it through:
            	// (this corresponds to the vertical 'pass through' arrows in the RHS paper)
            	set.add(d);
            	for (UseDefPair udPair : useToDefList) {
					CodeElement def = udPair.getDef();
					
					if (def.equals(de.codeElement)) {
						// this instruction redefined D, so we 
						// do *not* pass it through - this conditional has 
						// contradicted our assumption that D should be passed through,
						// so remove it from the set:
						set.remove(d);
						break;
					}
				}
            	
            	////////////////////////////////////////////////////////////////
            	// see if the taints associated with D also flow through to any 
            	// other domain elements:
            	
            	for (UseDefPair udPair : useToDefList) {
					CodeElement use = udPair.getUse();
					
					if (use.equals(de.codeElement)) {
						// ok, the d element flows to the def, so we add that def
						// and keep looking.
						DomainElement newDE = 
						     new DomainElement(udPair.getDef(), de.taintSource);
						set.add(domain.getMappedIndex(newDE));
					}
            	}	
            }
            return set;
        }
    }

    @Override
    public IUnaryFlowFunction getCallFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest,
            BasicBlockInContext<E> ret) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getCallToReturnFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getNormalFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IFlowFunction getReturnFlowFunction(
            BasicBlockInContext<E> call,
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
    }

}
