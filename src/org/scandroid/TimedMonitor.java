package org.scandroid;

import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

public class TimedMonitor implements IProgressMonitor {
	
	private final long deadline;

	/**
	 * @param seconds The time limit, in seconds.
	 */
	public TimedMonitor(long seconds) {
		deadline = System.currentTimeMillis() + (seconds * 1000L);
	}

	@Override
	public void beginTask(String task, int totalWork) {
	}

	@Override
	public boolean isCanceled() {
		return (System.currentTimeMillis() > deadline);
	}

	@Override
	public void done() {
	}

	@Override
	public void worked(int units) {
	}

}
