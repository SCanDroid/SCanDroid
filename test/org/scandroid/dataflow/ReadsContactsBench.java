package org.scandroid.dataflow;

import org.junit.Test;
import org.scandroid.SeparateEntryAnalysis;

public class ReadsContactsBench {

	@Test
	public void runReadsContacts() throws Throwable {
		SeparateEntryAnalysis.main(new String[] { 
				"--android-lib", "data/android-2.3.7_r1.jar", 
				"-l", 
				"--verbose", "INFO",
				"data/testdata/ReadsContactApp.apk" });
	}

}
