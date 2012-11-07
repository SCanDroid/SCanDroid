package org.scandroid.permissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class PScoutReader {
	private static final Logger logger = LoggerFactory
			.getLogger(PScoutReader.class);
	private static final Pattern METHOD_PATTERN = Pattern
			.compile("<(.*): (.*) (.*)\\((.*)\\)>.*");
	private static final URL ANDROID_JAR = PScoutReader.class.getClassLoader()
			.getResource("data/android-2.3.7_r1.jar");
	private static final ClassLoader ANDROID_CLASSLOADER = URLClassLoader
			.newInstance(new URL[] { ANDROID_JAR });
	private static final Pattern PERMISSION_PATTERN = Pattern
			.compile("Permission:(.*)");

	public static String pscout2descriptor(String pscoutString)
			throws ClassNotFoundException, NoSuchMethodException,
			SecurityException, IllegalAccessException {
		logger.trace("PScout line {}", pscoutString);
		// run the regex
		Matcher matcher = METHOD_PATTERN.matcher(pscoutString);
		matcher.matches();

		// pull components out of the match
		String classString = matcher.group(1);
		@SuppressWarnings("unused")
		String returnString = matcher.group(2);
		String methodNameString = matcher.group(3);
		String[] paramStrings = matcher.group(4).split(",");

		// resolve parameter classes
		Class<?>[] paramClasses;
		if (paramStrings.length == 1 && paramStrings[0].equals("")) {
			paramClasses = null;
		} else {
			paramClasses = new Class[paramStrings.length];
			for (int i = 0; i < paramStrings.length; i++) {
				String paramString = paramStrings[i];
				if (paramString.equals("")) {
					throw new IllegalStateException("Empty param string for"
							+ pscoutString);
				}
				Class<?> paramClass = ClassUtils.getClass(ANDROID_CLASSLOADER,
						paramString, false);
				paramClasses[i] = paramClass;
			}
		}

		// resolve enclosing class and look up method
		Class<?> clazz = ClassUtils.getClass(ANDROID_CLASSLOADER, classString,
				false);
		Method method = clazz.getMethod(methodNameString, paramClasses);
		boolean isStatic = Modifier.isStatic(method.getModifiers());
		method.setAccessible(true);

		// unreflect and grab JVM type
		MethodType methodType = MethodHandles.lookup().unreflect(method).type();

		// build the full descriptor
		StringBuilder descriptor = new StringBuilder();
		descriptor.append(clazz.getName().replace('.', '/')).append('.')
				.append(method.getName());
		if (isStatic) {
			descriptor.append(methodType.toMethodDescriptorString());
		} else {
			descriptor.append(methodType.dropParameterTypes(0, 1)
					.toMethodDescriptorString());
		}
		logger.trace("descriptor {}", descriptor);
		return descriptor.toString();
	}

	/**
	 * @param PScout
	 *            API mappings (e.g., results/gingerbread_allmappings)
	 * @return A map from permissions to sets of relevant method descriptors
	 * @throws IOException
	 * @throws IllegalAccessException
	 * @throws SecurityException
	 */
	public static Map<String, Set<String>> readAPIMappings(InputStream mappings)
			throws IOException, SecurityException, IllegalAccessException {
		// reporting statistics
		int permissions = 0;
		int apiCalls = 0;
		int classNotFound = 0;
		int noSuchMethod = 0;
		int noClassDef = 0;
		int unsatisfiedLink = 0;
		Set<String> classesNotFound = Sets.newTreeSet();

		HashMap<String, Set<String>> result = Maps.newHashMap();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				mappings));

		// assume first line is a permission
		String firstLine = reader.readLine();
		Matcher matcher = PERMISSION_PATTERN.matcher(firstLine);

		while (reader.ready()) {
			// invariant: always enter loop with a permission in the matcher
			if (!matcher.matches()) {
				IllegalStateException e = new IllegalStateException(
						"couldn't find permission definition");
				logger.error("reading API mappings", e);
				throw e;
			}
			permissions++;
			String permissionName = matcher.group(1);
			logger.trace("processing calls for permission {}", permissionName);
			// we don't care about # of callers for now, so skip line
			assert reader.ready();
			String discarded = reader.readLine();
			logger.trace("discarding {}", discarded);

			// now build the set
			Set<String> descriptorSet = Sets.newHashSet();
			result.put(permissionName, descriptorSet);

			while (reader.ready()) {
				// we add methods until we get to the end of the stream, or
				// another permission line
				String innerLine = reader.readLine();
				if (!matcher.reset(innerLine).matches()) {
					apiCalls++;
					try {
						descriptorSet.add(pscout2descriptor(innerLine));
						logger.trace("adding call {}", innerLine);
					} catch (ClassNotFoundException e) {
						logger.trace("ClassNotFoundException processing {}",
								innerLine);
//						logger.debug("exception", e);
						classNotFound++;
						classesNotFound.add(e.getMessage());
					} catch (NoSuchMethodException e) {
						logger.trace("NoSuchMethodException processing {}",
								innerLine);
						noSuchMethod++;
					} catch (NoClassDefFoundError e) {
						logger.trace("NoClassDefFoundError processing {}",
								innerLine);
						noClassDef++;
					} catch (UnsatisfiedLinkError e) {
						logger.trace("UnsatisfiedLinkError processing {}",
								innerLine);
						unsatisfiedLink++;
					} catch (IllegalStateException e) {
						logger.error("IllegalStateException processing {}",
								innerLine);
						throw e;
					}
				} else {
					break;
				}
			}
		}

		for (String clazz : classesNotFound) {
			logger.debug(clazz);
		}
		logger.debug("analyzed {} permissions with {} mapped API calls",
				permissions, apiCalls);
		logger.debug("ClassNotFoundException: {} failed", classNotFound);
		logger.debug("NoSuchMethodException: {} failed", noSuchMethod);
		logger.debug("NoClassDefFoundError: {} failed", noClassDef);
		logger.debug("UnsatisfiedLinkError: {} failed", unsatisfiedLink);		
		return result;
	}
}
