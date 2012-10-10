package org.scandroid;
/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Adam Fuchs          <afuchs@cs.umd.edu>
 *  Avik Chaudhuri      <avik@cs.umd.edu>
 *  Steve Suh           <suhsteve@gmail.com>
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import prefixTransfer.InstanceKeySite;
import prefixTransfer.PrefixTransferFunctionProvider;
import prefixTransfer.PrefixVariable;
import prefixTransfer.UriPrefixContextSelector;
import prefixTransfer.UriPrefixTransferGraph;
import util.AndroidAnalysisContext;
import util.EmptyProgressMonitor;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.graph.DataflowSolver;
import com.ibm.wala.dataflow.graph.IKilldallFramework;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.CancelRuntimeException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;


public class UriPrefixAnalysis {

    public static Map<InstanceKey,String> runAnalysis(AndroidAnalysisContext<IExplodedBasicBlock> appLoader) throws CancelRuntimeException
    {
        return runAnalysisHelper(appLoader.cg, appLoader.pa);
    }

    public static ArrayList<InstanceKey> locateKeys(Map<InstanceKey,String> prefixes, String s) {
        ArrayList<InstanceKey> keylist = new ArrayList<InstanceKey>();
        for (Entry<InstanceKey,String> e : prefixes.entrySet()) {
            if (e.getValue().contains(s))
                keylist.add(e.getKey());//pa.getInstanceKeyMapping().getMappedIndex(e.getKey())
        }
        return keylist;
    }

    public static Map<InstanceKey,String> runAnalysisHelper(CallGraph cg, PointerAnalysis pa) throws CancelRuntimeException
    {

        System.out.println("*******************************************************");
        System.out.println("* Prefix Analysis: Constructing Prefix Transfer Graph *");


        final Graph<InstanceKeySite> g = new UriPrefixTransferGraph(pa);
        System.out.println("* The Graph:                                          *");
        System.out.println("*******************************************************");
        Iterator<InstanceKeySite> iksI = g.iterator();
        while (iksI.hasNext()) {
            InstanceKeySite iks = iksI.next();
            System.out.println("# " + iks);
            Iterator<InstanceKeySite> edgesI = g.getSuccNodes(iks);
            while (edgesI.hasNext()) {
                System.out.println("? \t -->" + edgesI.next());
            }
        }
        final PrefixTransferFunctionProvider tfp = new PrefixTransferFunctionProvider();

        IKilldallFramework<InstanceKeySite, PrefixVariable> framework = new IKilldallFramework<InstanceKeySite, PrefixVariable>()
        {

            public Graph<InstanceKeySite> getFlowGraph() {
                return g;
            }

            public ITransferFunctionProvider<InstanceKeySite, PrefixVariable> getTransferFunctionProvider() {
                return tfp;
            }

        };

        DataflowSolver<InstanceKeySite, PrefixVariable> dfs = new DataflowSolver<InstanceKeySite, PrefixVariable>(framework){

            @Override
            protected PrefixVariable makeEdgeVariable(InstanceKeySite src,
                    InstanceKeySite dst) {
                return new PrefixVariable(){};
            }

            @Override
            protected PrefixVariable makeNodeVariable(InstanceKeySite n,
                    boolean IN) {
                // TODO Auto-generated method stub
                PrefixVariable var = new PrefixVariable(){};
//              if (n instanceof StringBuilderToStringInstanceKeySite) var.setOrderNumber(0);
//              else var.setOrderNumber(10);
//              var.add(3);
                return var;
            }

            @Override
            protected PrefixVariable[] makeStmtRHS(int size) {
                return new PrefixVariable[size];
            }

        };

        System.out.println("\n**************************************************");
        System.out.println("* Running Analysis");

        try {
            dfs.solve(new EmptyProgressMonitor());
        } catch (CancelException e) {
            throw new CancelRuntimeException(e);
        }
        Map<InstanceKey,String> prefixes = new HashMap<InstanceKey,String>();
        iksI = g.iterator();
        while (iksI.hasNext()) {
            InstanceKeySite iks = iksI.next();
            prefixes.put(pa.getInstanceKeyMapping().getMappedObject(iks.instanceID()), dfs.getOut(iks).knownPrefixes.get(iks.instanceID()));
//          System.out.println(iks + " ~> " + dfs.getOut(iks));
        }
//      System.out.println("\nLocalPointerKeys that point to String constants: \n" + stringConstants);

        for (Entry<InstanceKey,String> e : prefixes.entrySet()) {
            System.out.println(pa.getInstanceKeyMapping().getMappedIndex(e.getKey()) + "\t~> " + e.getValue());
        }

        // TODO: populate prefixes
        return prefixes;
    }


}
