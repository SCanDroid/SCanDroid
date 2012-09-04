/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>)
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

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import synthMethod.MethodAnalysis;
import synthMethod.XMLMethodSummaryWriter;
import util.AndroidAppLoader;
import util.CLI;

import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;
import flow.FlowAnalysis;
import flow.InflowAnalysis;
import flow.OutflowAnalysis;
import flow.types.FlowType;

public class SeparateEntryAnalysis {
    public static void main(String[] args) throws Exception {
        CLI.parseArgs(args, true);

        System.out.println("Loading app.");
        AndroidAppLoader<IExplodedBasicBlock> loader =
                new AndroidAppLoader<IExplodedBasicBlock>(CLI.getClasspath());
        if (loader.entries == null || loader.entries.size() == 0) {
            throw new IOException("No Entrypoints Detected!");
        }
        MethodAnalysis<IExplodedBasicBlock> methodAnalysis =
           new MethodAnalysis<IExplodedBasicBlock>();
        
        if(CLI.hasOption("separate-entries")) {
            int i = 1;
            for (Entrypoint entry : loader.entries) {
                System.out.println("** Processing entry point " + i + "/" +
                        loader.entries.size() + ": " + entry);
                LinkedList<Entrypoint> localEntries = new LinkedList<Entrypoint>();
                localEntries.add(entry);
                analyze(loader, localEntries, methodAnalysis);
                i++;
            }
        } else {
            analyze(loader, loader.entries, methodAnalysis);
            XMLMethodSummaryWriter.createXML(methodAnalysis);
        }
    }

    static void 
        analyze(AndroidAppLoader<IExplodedBasicBlock> loader,
                 LinkedList<Entrypoint> localEntries, 
                 MethodAnalysis<IExplodedBasicBlock> methodAnalysis) {
        try {
            loader.buildGraphs(localEntries);
    		// load the permissions
    		Set<String> manifestFilenames = new HashSet<String>();
    		Permissions perms = Permissions.load(manifestFilenames);

            System.out.println("Supergraph size = "
                    + loader.graph.getNumberOfNodes());

             System.out.println("Running prefix analysis.");
             Map<InstanceKey, String> prefixes =
                 UriPrefixAnalysis.runAnalysisHelper(loader.cg, loader.pa);
             System.out.println("Number of prefixes = " + prefixes.values().size());

            System.out.println("Running inflow analysis.");
            Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType, Set<CodeElement>>> initialTaints = InflowAnalysis
                    .analyze(loader, prefixes);
            System.out.println("  Initial taint size = "
                    + initialTaints.size());
                       
            System.out.println("Running flow analysis.");
            IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
            TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> 
              flowResult = FlowAnalysis.analyze(loader, initialTaints, domain, methodAnalysis);

            System.out.println("Running outflow analysis.");
            Map<FlowType, Set<FlowType>> permissionOutflow = OutflowAnalysis
                    .analyze(loader, flowResult, domain);
            System.out.println("  Permission outflow size = "
                    + permissionOutflow.size());
            
            System.out.println("Running Checker.");
    		Checker.check(permissionOutflow, perms, prefixes);

            
            System.out.println();
            System.out
                    .println("================================================================");
            System.out.println();

            for (Map.Entry<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType, Set<CodeElement>>> e : initialTaints
                    .entrySet()) {
                System.out.println(e.getKey());
                for (Map.Entry<FlowType, Set<CodeElement>> e2 : e.getValue()
                        .entrySet()) {
                    System.out
                            .println(e2.getKey() + " <- " + e2.getValue());
                }
            }
            for (Map.Entry<FlowType, Set<FlowType>> e : permissionOutflow
                    .entrySet()) {
                System.out.println(e.getKey());
                for (FlowType t : e.getValue()) {
                    System.out.println("    --> " + t);
                }
            }            
            
//            System.out.println("DOMAIN ELEMENTS");
//            for (int i = 1; i < domain.getSize(); i++) {
//            	System.out.println("#"+i+" - "+domain.getMappedObject(i));
//            }
//            System.out.println("------");
//            for (CGNode n:loader.cg.getEntrypointNodes()) {
//            	for (int i = 0; i < 6; i++)
//            	{
//            		try {
//            		System.out.println(i+": ");
//            		String[] s = n.getIR().getLocalNames(n.getIR().getInstructions().length-1, i);
//            		
//            		for (String ss:s)
//            			System.out.println("\t"+ss);
//            		}
//            		catch (Exception e) {
//            			System.out.println("exception at " + i);
//            		}
//            	}
//            }
//            
//            System.out.println("------");
//            for (CGNode n:loader.cg.getEntrypointNodes()) {
//            	for (SSAInstruction ssa: n.getIR().getInstructions()) {
////            		System.out.println("Definition " + ssa.getDef() + ":"+ssa);
//            		System.out.println("Definition "+ssa);
//            	}
//            }
            
        } catch (com.ibm.wala.util.debug.UnimplementedError e) {
            e.printStackTrace();
        } catch (CancelException e){
            System.err.println("Canceled (" + e.getMessage() + ").");
        }
    }
}
