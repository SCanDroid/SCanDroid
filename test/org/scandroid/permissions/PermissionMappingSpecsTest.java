package org.scandroid.permissions;

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.scandroid.PermissionMappingSpecs;
import org.scandroid.spec.ISpecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionMappingSpecsTest {
	private static final Logger logger = LoggerFactory.getLogger(PermissionMappingSpecsTest.class);
	
	private ISpecs specs;

	@Before
	public void setUp() throws Exception {
		final InputStream mappingStream = PermissionMappingSpecsTest.class.getResourceAsStream("/data/gingerbread_allmappings_HashMap.bin");
		Assert.assertNotNull(mappingStream);
		specs = new PermissionMappingSpecs(mappingStream);
	}

	@Test
	public void testGetSourceSpecs() {
		logger.info("number of sources: {}", specs.getSourceSpecs().length);
	}

	@Test
	public void testGetSinkSpecs() {
		logger.info("number of sinks: {}", specs.getSinkSpecs().length);
	} 

}
