package org.scandroid.testing;

public class NestedInvocations {
	
	public void indirectCallArgSink(String s) {
		directCallArgSink(s);
	}

	private void directCallArgSink(String s) {
		SourceSink.sink(s);
	}	
	
	public Integer indirectCallRetSource() {
		return directCallRetSource();
	}
	
	private Integer directCallRetSource() {
		return SourceSink.source();
	}
	
	public char[] indirectCallArgSource() {
		return directCallArgSource();		
	}

	private char[] directCallArgSource() {
		char[] buf = new char[] {};
		SourceSink.load(buf);
		return buf;
	}

}
