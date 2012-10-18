package synthMethod;

import java.io.File;
import java.net.URI;

import org.scandroid.util.ISCanDroidOptions;

import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.util.io.FileProvider;

public abstract class DefaultSCanDroidOptions implements ISCanDroidOptions {

	@Override
	public boolean pdfCG() {
		return false;
	}

	@Override
	public boolean pdfPartialCG() {
		return false;
	}

	@Override
	public boolean pdfOneLevelCG() {
		return false;
	}

	@Override
	public boolean systemToApkCG() {
		return false;
	}

	@Override
	public boolean stdoutCG() {
		return false;
	}

	@Override
	public boolean includeLibrary() {
		// TODO is this right? we haven't summarized with CLI options set, so
		// this is what we've been doing...
		return true;
	}

	@Override
	public boolean separateEntries() {
		return false;
	}

	@Override
	public boolean ifdsExplorer() {
		return false;
	}

	@Override
	public boolean addMainEntrypoints() {
		return false;
	}

	@Override
	public boolean useThreadRunMain() {
		return false;
	}

	@Override
	public boolean stringPrefixAnalysis() {
		return false;
	}

	@Override
	public boolean testCGBuilder() {
		return false;
	}

	@Override
	public boolean useDefaultPolicy() {
		return false;
	}

	@Override
	public abstract URI getClasspath();

	@Override
	public String getFilename() {
		return new File(getClasspath()).getName();
	}

	@Override
	public URI getAndroidLibrary() {
		try {
			return new FileProvider().getResource("data/android-2.3.7_r1.jar").toURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ReflectionOptions getReflectionOptions() {
		return ReflectionOptions.NONE;
	}

	@Override
	public URI getSummariesURI() {
		try {
			return new FileProvider().getResource("data/MethodSummaries.xml").toURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
