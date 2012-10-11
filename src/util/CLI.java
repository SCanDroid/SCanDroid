/*
 *
 * Copyright (c) 2009-2012,
 *
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

package util;

import java.io.File;
import java.util.*;

import org.apache.commons.cli.*;
import org.apache.log4j.BasicConfigurator;

import static java.lang.System.setProperty;

public class CLI {

	private static Options options = new Options();;
	private static CommandLineParser parser = new PosixParser();
	private static CommandLine line;
	private static String filename;
	private static String classpath;
    private static final String USAGE = "[options] <.apk or .jar>";

	
	public static void parseArgs(String[] args, boolean reqArgs) {
		CLI.setupOptions();
		try {
			line = parser.parse(options, args);
		}
		catch (ParseException exp) {
			System.err.println("Unexpected exception: " + exp.getMessage());
			System.err.println("Usage: " + USAGE);
			System.exit(0);
		}
		
    	if (CLI.hasOption("help")) {
    		HelpFormatter formatter = new HelpFormatter();
    		formatter.printHelp( USAGE, options );
    		System.exit(0);
    	}
    	
    	if (CLI.hasOption("verbose")) {
    		
    		setProperty("LOG_LEVEL", line.getOptionValue("verbose"));
    	}
    	
    	if (!CLI.hasOption("android-lib")) {
    		System.err.println("Please specify an android library");
    		System.exit(0);
    	}
		
		fetchClasspath(reqArgs);
		fetchFilename();
		if (reqArgs && !(filename.endsWith(".apk") || filename.endsWith(".jar"))) {
			System.err.println("Usage: " + USAGE);
			System.exit(0);
		}
	}
	
	private static void setupOptions() {
		options.addOption("h", "help", false, "print this message" );
		options.addOption(OptionBuilder.withLongOpt( "verbose" ).withDescription( "set desired debugging outout level \"ERROR (0), WARNING (1), INFO (2), DEBUG (3)\"" ).hasArg().withArgName("level").create());
		options.addOption("c", "call-graph", false, "create full call graph pdf");
		options.addOption("p", "partial-call-graph", false, "create partial call graph pdf (Application only)");
		options.addOption("o", "one-level-call-graph", false, "create one level call graph pdf (Application + 1 level of System calls)");
		options.addOption("s", "system-to-apk-call-graph", false, "create system to apk callgraph (System + 1 level of Application calls)");		
		options.addOption("l", "include-library", false, "analyze library in flow analysis");
		options.addOption("e", "separate-entries", false, "analyze each entry point separately");
		options.addOption("i", "IFDS-Explorer", false, "bring up a gui to analyze domainelements for flow analysis");
		options.addOption("m", "main-entrypoint", false, "look for main methods and add them as entrypoints");
		options.addOption("a", "stdout-call-graph", false, "output full call graph to stdout");
		options.addOption("t", "thread-run-main", false, "use ServerThread.run as the entry point for analysis");
		options.addOption("x", "prefix-analysis", false, "run string prefix analysis");
        options.addOption("f", "summaries-file", true, "Use the specified summaries xml file");
        options.addOption(OptionBuilder.withLongOpt("test-cgb")
        		           .withDescription("Only load the call graph, exit status indicates success")
        		           .create());
        options.addOption("y", "check-policy", false, "Check conformance with built-in policy");
		
		options.addOption(OptionBuilder.withLongOpt( "android-lib" ).withDescription( "include ALIB in scope of analysis" ).hasArg().withArgName("ALIB").create() );
		options.addOption(OptionBuilder.withLongOpt( "reflection" ).withDescription( "FULL, NO_FLOW_TO_CASTS, NO_METHOD_INVOKE, NO_FLOW_TO_CASTS_NO_METHOD_INVOKE, ONE_FLOW_TO_CASTS_NO_METHOD_INVOKE, NO_STRING_CONSTANTS, NONE (Default)").hasArg().withArgName("option").create() );	    
		
	}
		
	public static boolean hasOption(String s) {
		return line != null && line.hasOption(s);
	}
	
	public static String getOption(String s) {
		return  line.getOptionValue(s);
	}
	
	private static void fetchClasspath(boolean reqArgs) {
		//getArgs() returns all args that are not recognized;
		String [] myargs = line.getArgs();
		if ((myargs.length != 1 || !(myargs[0].endsWith(".apk") || myargs[0].endsWith(".jar"))) && reqArgs) {
			System.err.println("Usage: " + USAGE);
			System.exit(0);
		}
		if(myargs.length > 0)
		    classpath = myargs[0];
	}
	
	private static void fetchFilename() {
	    if(classpath == null) return;
		String[] path = classpath.split(File.separatorChar=='\\' ? "\\\\" : File.separator );
		if(path.length > 0)
		    filename = path[path.length-1];
	}
	
	public static String getClasspath() {
		return classpath;
	}
	
	public static String getFilename() {
		return filename;
	}
	
}
