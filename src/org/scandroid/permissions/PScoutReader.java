package org.scandroid.permissions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ClassUtils;

public class PScoutReader {
	private static final Pattern PATTERN = Pattern
			.compile("<(.*): (.*) (.*)\\((.*)\\)>.*");
	private static final URL ANDROID_JAR = PScoutReader.class.getClassLoader()
			.getResource("data/android-2.3.7_r1.jar");
	private static final ClassLoader ANDROID_CLASSLOADER = URLClassLoader
			.newInstance(new URL[] { ANDROID_JAR });

	public static String pscout2descriptor(String pscoutString)
			throws ClassNotFoundException, NoSuchMethodException,
			SecurityException, IllegalAccessException {
		// run the regex
		Matcher matcher = PATTERN.matcher(pscoutString);
		matcher.matches();

		// pull components out of the match
		String classString = matcher.group(1);
		@SuppressWarnings("unused")
		String returnString = matcher.group(2);
		String methodNameString = matcher.group(3);
		String[] paramStrings = matcher.group(4).split(",");

		// resolve parameter classes
		Class<?>[] paramClasses = new Class[paramStrings.length];
		for (int i = 0; i < paramStrings.length; i++) {
			String paramString = paramStrings[i];
			Class<?> paramClass = ClassUtils.getClass(ANDROID_CLASSLOADER,
					paramString, false);
			paramClasses[i] = paramClass;
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
		return descriptor.toString();
	}
}
