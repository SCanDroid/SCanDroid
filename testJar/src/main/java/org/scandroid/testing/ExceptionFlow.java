package org.scandroid.testing;

public class ExceptionFlow {

	public static void exceptionFlow(String[] args) {
		SourceSink.sink(useFlow(args));
	}

	private static String useFlow(String[] args) {
		try {
			thrower(args[0]);
		} catch (Exception e) {
			return e.getMessage();
		}
		return null;
	}
	
	public static void thrower(String str) throws Exception {
		throw new Exception(str);
	}
}
