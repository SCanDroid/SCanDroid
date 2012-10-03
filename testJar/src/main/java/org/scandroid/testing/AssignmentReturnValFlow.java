package org.scandroid.testing;

public class AssignmentReturnValFlow {
	public static void flow(String[] args) {
		SourceSink.sink(useFlow(args));
	}

	private static String useFlow(String[] args) {
		String foo;
		String bar = (foo = args[0]);
		return bar;
	}
}
