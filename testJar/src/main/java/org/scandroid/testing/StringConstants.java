package org.scandroid.testing;

public class StringConstants {
	public static final String FOO = "FOO";
	public static final String BAR = "BAR";
	
	public String returnFoo() {
		return FOO;
	}
	
	public String returnBar() {
		return BAR;
	}
	
	public String returnSomething() {
		return returnFoo();
	}
}
