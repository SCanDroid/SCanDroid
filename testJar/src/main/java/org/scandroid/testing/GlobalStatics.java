package org.scandroid.testing;

public class GlobalStatics {
	public static String FOO;
	
	public static Integer getFoo() {
		// probably needs a catch statement to handle NumberFormatExceptions
		return Integer.parseInt(FOO);
	}
	
	public static void setFoo(Integer foo) {
		GlobalStatics.FOO = foo + "";
	}
}
