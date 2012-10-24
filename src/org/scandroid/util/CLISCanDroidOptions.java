package org.scandroid.util;

import java.io.File;
import java.net.URI;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;

@SuppressWarnings("static-access")
public class CLISCanDroidOptions implements ISCanDroidOptions {
	private static final String VERBOSE = "verbose";
	private static final String REFLECTION = "reflection";
	private static final String ANDROID_LIB = "android-lib";
	private static final String CHECK_POLICY = "check-policy";
	private static final String TEST_CGB = "test-cgb";
	private static final String SUMMARIES_FILE = "summaries-file";
	private static final String PREFIX_ANALYSIS = "prefix-analysis";
	private static final String THREAD_RUN_MAIN = "thread-run-main";
	private static final String STDOUT_CALL_GRAPH = "stdout-call-graph";
	private static final String MAIN_ENTRYPOINT = "main-entrypoint";
	private static final String IFDS_EXPLORER = "IFDS-Explorer";
	private static final String SEPARATE_ENTRIES = "separate-entries";
	private static final String INCLUDE_LIBRARY = "include-library";
	private static final String SYSTEM_TO_APK_CALL_GRAPH = "system-to-apk-call-graph";
	private static final String ONE_LEVEL_CALL_GRAPH = "one-level-call-graph";
	private static final String PARTIAL_CALL_GRAPH = "partial-call-graph";
	private static final String CALL_GRAPH = "call-graph";

	private CommandLineParser parser = new PosixParser();
	private CommandLine line;
	private URI classpath;
	private String filename;
	private URI androidLib;
	private URI summariesFile;
	private ReflectionOptions reflectionOptions;
	private static final String USAGE = "[options] <.apk or .jar>";

	private final Options options = new Options();
	{
		options.addOption("h", "help", false, "print this message");
		options.addOption(OptionBuilder
				.withLongOpt(VERBOSE)
				.withDescription(
						"logging level (default INFO) [OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL]")
				.hasArg().withArgName("level").create());
		options.addOption("c", CALL_GRAPH, false, "create full call graph pdf");
		options.addOption("p", PARTIAL_CALL_GRAPH, false,
				"create partial call graph pdf (Application only)");
		options.addOption("o", ONE_LEVEL_CALL_GRAPH, false,
				"create one level call graph pdf (Application + 1 level of System calls)");
		options.addOption("s", SYSTEM_TO_APK_CALL_GRAPH, false,
				"create system to apk callgraph (System + 1 level of Application calls)");
		options.addOption("l", INCLUDE_LIBRARY, false,
				"analyze library in flow analysis");
		options.addOption("e", SEPARATE_ENTRIES, false,
				"analyze each entry point separately");
		options.addOption("i", IFDS_EXPLORER, false,
				"bring up a gui to analyze domainelements for flow analysis");
		options.addOption("m", MAIN_ENTRYPOINT, false,
				"look for main methods and add them as entrypoints");
		options.addOption("a", STDOUT_CALL_GRAPH, false,
				"output full call graph to stdout");
		options.addOption("t", THREAD_RUN_MAIN, false,
				"use ServerThread.run as the entry point for analysis");
		options.addOption("x", PREFIX_ANALYSIS, false,
				"run string prefix analysis");
		options.addOption("f", SUMMARIES_FILE, true,
				"Use the specified summaries xml file");
		options.addOption(OptionBuilder
				.withLongOpt(TEST_CGB)
				.withDescription(
						"Only load the call graph, exit status indicates success")
				.create());
		options.addOption("y", CHECK_POLICY, false,
				"Check conformance with built-in policy");

		options.addOption(OptionBuilder.withLongOpt(ANDROID_LIB)
				.withDescription("include ALIB in scope of analysis").hasArg()
				.withArgName("ALIB").create());
		options.addOption(OptionBuilder
				.withLongOpt(REFLECTION)
				.withDescription(
						"FULL, NO_FLOW_TO_CASTS, NO_METHOD_INVOKE, NO_FLOW_TO_CASTS_NO_METHOD_INVOKE, ONE_FLOW_TO_CASTS_NO_METHOD_INVOKE, NO_STRING_CONSTANTS, NONE (Default)")
				.hasArg().withArgName("option").create());
	}

	public CLISCanDroidOptions(String[] args, boolean reqArgs) {
		try {
			line = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println("Unexpected exception: " + exp.getMessage());
			System.err.println("Usage: " + USAGE);
			System.exit(0);
		}

		if (hasOption("help")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(USAGE, options);
			System.exit(0);
		}

		// handle verbosity
		// parse this arg as a Logback level, then set the root logger level
		// appropriately
		Level level = Level.toLevel(getOption(VERBOSE), Level.INFO);
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(level);


		if (!hasOption(ANDROID_LIB)) {
			System.err.println("Please specify an android library");
			System.exit(0);
		}

		classpath = processClasspath(reqArgs);
		filename = processFilename();
		androidLib = processURIArg(getOption(ANDROID_LIB));
		summariesFile = processURIArg(getOption(SUMMARIES_FILE));
		reflectionOptions = processReflectionOptions();

		if (reqArgs
				&& !(filename.endsWith(".apk") || filename.endsWith(".jar"))) {
			System.err.println("Usage: " + USAGE);
			System.exit(0);
		}
	}

	private URI processURIArg(String arg) {
		if (arg == null) {
			return null;
		} else {
			return new File(arg).toURI();
		}
	}

	private URI processClasspath(boolean reqArgs) {
		// getArgs() returns all args that are not recognized;
		String[] myargs = line.getArgs();
		if ((myargs.length != 1 || !(myargs[0].endsWith(".apk") || myargs[0]
				.endsWith(".jar"))) && reqArgs) {
			System.err.println("Usage: " + USAGE);
			System.exit(0);
		}
		return processURIArg(myargs[0]);
	}

	private String processFilename() {
		if (classpath == null)
			return null;
		return new File(classpath).getName();
	}

	private ReflectionOptions processReflectionOptions() {
		final String reflection = getOption(REFLECTION);
		if (reflection == null) {
			return ReflectionOptions.NONE;
		} else {
			return ReflectionOptions.valueOf(reflection);
		}
	}

	private boolean hasOption(String s) {
		return line != null && line.hasOption(s);
	}

	private String getOption(String s) {
		return line.getOptionValue(s);
	}

	@Override
	public boolean pdfCG() {
		return hasOption(CALL_GRAPH);
	}

	@Override
	public boolean pdfPartialCG() {
		return hasOption(PARTIAL_CALL_GRAPH);
	}

	@Override
	public boolean pdfOneLevelCG() {
		return hasOption(ONE_LEVEL_CALL_GRAPH);
	}

	@Override
	public boolean systemToApkCG() {
		return hasOption(SYSTEM_TO_APK_CALL_GRAPH);
	}

	@Override
	public boolean stdoutCG() {
		return hasOption(STDOUT_CALL_GRAPH);
	}

	@Override
	public boolean includeLibrary() {
		return hasOption(INCLUDE_LIBRARY);
	}

	@Override
	public boolean separateEntries() {
		return hasOption(SEPARATE_ENTRIES);
	}

	@Override
	public boolean ifdsExplorer() {
		return hasOption(IFDS_EXPLORER);
	}

	@Override
	public boolean addMainEntrypoints() {
		return hasOption(MAIN_ENTRYPOINT);
	}

	@Override
	public boolean useThreadRunMain() {
		return hasOption(THREAD_RUN_MAIN);
	}

	@Override
	public boolean stringPrefixAnalysis() {
		return hasOption(PREFIX_ANALYSIS);
	}

	@Override
	public boolean testCGBuilder() {
		return hasOption(TEST_CGB);
	}

	@Override
	public boolean useDefaultPolicy() {
		return hasOption(CHECK_POLICY);
	}

	@Override
	public URI getClasspath() {
		return classpath;
	}

	@Override
	public String getFilename() {
		return filename;
	}

	@Override
	public URI getAndroidLibrary() {
		return androidLib;
	}

	@Override
	public ReflectionOptions getReflectionOptions() {
		return reflectionOptions;
	}

	@Override
	public URI getSummariesURI() {
		return summariesFile;
	}
	
	@Override
	public boolean classHierarchyWarnings() {
		// TODO Auto-generated method stub
		return true;
	}
	
	@Override
	public boolean cgBuilderWarnings() {
		// TODO Auto-generated method stub
		return true;
	}
}
