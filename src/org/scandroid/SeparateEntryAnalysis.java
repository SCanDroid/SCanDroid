package org.scandroid;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spec.AndroidSpecs;
import spec.ISpecs;
import synthMethod.MethodAnalysis;
import util.AndroidAppLoader;
import util.CLI;

import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
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
	private static final Logger logger = LoggerFactory.getLogger(SeparateEntryAnalysis.class);
	
    public static void main(String[] args) throws Exception {
    	BasicConfigurator.configure();
        CLI.parseArgs(args, true);

        logger.info("Loading app.");
        AndroidAppLoader<IExplodedBasicBlock> loader =
                new AndroidAppLoader<IExplodedBasicBlock>(CLI.getClasspath());
        if (loader.entries == null || loader.entries.size() == 0) {
            throw new IOException("No Entrypoints Detected!");
        }
        MethodAnalysis<IExplodedBasicBlock> methodAnalysis = null;
           //new MethodAnalysis<IExplodedBasicBlock>();
        for (Entrypoint entry : loader.entries) {
            logger.info("Entry point: " + entry);
        }
        
        String summariesFileName = CLI.getOption("summaries-file");
        InputStream summaryStream = null;
        if ( null != summariesFileName ) {
        	File summariesFile = new File(summariesFileName);
        	
        	if ( !summariesFile.exists() ) {
        		logger.error("Could not find summaries file: "+summariesFileName);
        		System.exit(1);
        	}
        	
        	summaryStream = new FileInputStream(summariesFile);
        }
        
        if(CLI.hasOption("separate-entries")) {
            int i = 1;
            for (Entrypoint entry : loader.entries) {
                logger.info("** Processing entry point " + i + "/" +
                        loader.entries.size() + ": " + entry);
                LinkedList<Entrypoint> localEntries = new LinkedList<Entrypoint>();
                localEntries.add(entry);
                analyze(loader, localEntries, methodAnalysis, summaryStream);
                i++;
            }
        } else {
            analyze(loader, loader.entries, methodAnalysis, summaryStream);
        }
    }

    /**
     * @param loader
     * @param localEntries
     * @param methodAnalysis
     * @return the number of permission outflows detected
     */
    public static int 
        analyze(AndroidAppLoader<IExplodedBasicBlock> loader,
                 LinkedList<Entrypoint> localEntries, 
                 MethodAnalysis<IExplodedBasicBlock> methodAnalysis,
                 InputStream summariesStream) {
        try {
            loader.buildGraphs(localEntries, summariesStream);

            logger.info("Supergraph size = "
                    + loader.graph.getNumberOfNodes());

            Map<InstanceKey, String> prefixes;
            if(CLI.hasOption("prefix-analysis")) {
                logger.info("Running prefix analysis.");
                prefixes = UriPrefixAnalysis.runAnalysisHelper(loader.cg, loader.pa);
                logger.info("Number of prefixes = " + prefixes.values().size());
            } else {
                prefixes = new HashMap<InstanceKey, String>();
            }
            
            
            ISpecs specs = new AndroidSpecs();
             
            logger.info("Running inflow analysis.");
            Map<BasicBlockInContext<IExplodedBasicBlock>, 
                Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = 
                  InflowAnalysis.analyze(loader, prefixes, specs);
            
            logger.info("  Initial taint size = "
                    + initialTaints.size());
                       
            logger.info("Running flow analysis.");
            IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
            TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> 
              flowResult = FlowAnalysis.analyze(loader, initialTaints, domain, methodAnalysis, null);

            logger.info("Running outflow analysis.");
            Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = OutflowAnalysis
                    .analyze(loader, flowResult, domain, specs);
            logger.info("  Permission outflow size = "
                    + permissionOutflow.size());
            
            //logger.info("Running Checker.");
    		//Checker.check(permissionOutflow, perms, prefixes);

            
            logger.info("");
            logger.info("================================================================");
            logger.info("");

            for (Map.Entry<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> e : initialTaints
                    .entrySet()) {
                logger.info(e.getKey().toString());
                for (Map.Entry<FlowType<IExplodedBasicBlock>, Set<CodeElement>> e2 : e.getValue()
                        .entrySet()) {
                    logger.info(e2.getKey() + " <- " + e2.getValue());
                }
            }
            for (Map.Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> e : permissionOutflow
                    .entrySet()) {
                logger.info(e.getKey().toString());
                for (FlowType t : e.getValue()) {
                    logger.info("    --> " + t);
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
            return permissionOutflow.size();
        } catch (com.ibm.wala.util.debug.UnimplementedError e) {
            logger.error("exception during analysis", e);
        } catch (CancelException e){
            logger.warn("Canceled", e);
        }
        return 0;
    }
}
