package org.scandroid.permissions;

import static org.junit.Assert.*;

import org.junit.Test;

public class PScoutReaderTest {

	@Test
	public void getAccountsByFeatures() throws Throwable {
		final String pscoutString = "<android.accounts.IAccountManager$Stub$Proxy: void getAccountsByFeatures(android.accounts.IAccountManagerResponse,java.lang.String,java.lang.String[])> ()";
		final String expectedDescriptor = "android/accounts/IAccountManager$Stub$Proxy.getAccountsByFeatures(Landroid/accounts/IAccountManagerResponse;Ljava/lang/String;[Ljava/lang/String;)V";
		assertTrue(PScoutReader.pscout2descriptor(pscoutString).equals(
				expectedDescriptor));
	}

	@Test
	public void setMasterSyncAutomatically() throws Throwable {
		final String pscoutString = "<android.content.ContentResolver: void setMasterSyncAutomatically(boolean)> (5)";
		final String expectedDescriptor = "android/content/ContentResolver.setMasterSyncAutomatically(Z)V";
		assertTrue(PScoutReader.pscout2descriptor(pscoutString).equals(
				expectedDescriptor));
	}

	@Test
	public void generateMapping() throws Throwable {
		PScoutReader.readAPIMappings(this.getClass().getResourceAsStream(
				"/data/gingerbread_allmappings"));
	}
}
