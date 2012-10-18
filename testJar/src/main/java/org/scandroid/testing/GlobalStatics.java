package org.scandroid.testing;

public class GlobalStatics {
	public static Object FOO;
	
	public static Object getFoo() {
		return FOO;
	}
	
	public static void setFoo(Object foo) {
		GlobalStatics.FOO = foo;
	}
}
