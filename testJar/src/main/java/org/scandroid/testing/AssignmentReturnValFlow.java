package org.scandroid.testing;

public class AssignmentReturnValFlow {
	public static void flow(String[] args) {
		SourceSink.sink(assignVal(args));
	}

	public static String assignVal(String[] args) {
		String foo;
		String bar = (foo = args[0]);
		return bar;
	}
}
