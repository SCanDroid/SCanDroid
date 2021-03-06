/**
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
 *  Steve Suh    <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */
package org.scandroid;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.CancelRuntimeException;

/**
 * @author creswick
 * 
 */
public class JarAnalysis {

	// time limit for summarizing one method, in seconds:
	private static final int TIME_LIMIT = 15 * 60;
	//private static final int THREAD_COUNT = 70;
	private static int THREAD_COUNT = 3;
	protected static final String PRG = "[PRG] ";
	private static String OUTPUT_DIR = "results";

	/**
	 * @param args
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static void main(String[] args) throws IOException,
			ClassNotFoundException {
		THREAD_COUNT = Integer.parseInt(args[0]);
		final String appJar = args[1];
		final String pkglistFile = args[2];
		Set<String> interesting = loadLinesAsSet(pkglistFile);

		analyzeJar(appJar, interesting);
	}

	public static void analyzeJar(final String appJar)
			throws ClassNotFoundException, IOException {
		Set<String> mt = Sets.newHashSet();
		analyzeJar(appJar, mt);
	}

	public static void analyzeJar(final String appJar, Set<String> interestingPkgs)
			throws ClassNotFoundException, IOException {
		final Multimap<String, String> pkgMethods = getMethodsByPackage(appJar);

		Set<Future<?>> futures = Sets.newHashSet();
		// ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
		
		int tcount = Math.min(interestingPkgs.size(), THREAD_COUNT);
		ExecutorService pool = Executors.newFixedThreadPool(tcount);

//		Set<String> fullPkgSet = pkgMethods.keySet();
//
//		SetView<String> interestingPkgs = Sets
//				.difference(fullPkgSet, blacklist);
		System.out.println("Working on these packages:");
		for (final String pkg : interestingPkgs) {
			System.out.println("   "+pkg);
		}
		System.out.println("--------------------------");
		for (final String pkg : interestingPkgs) {
			Runnable runner = new Runnable() {
				public void run() {
					System.out.println(PRG + "Starting work on: " + pkg);
					Collection<String> methodDescriptors = pkgMethods.get(pkg);
					try {
						Summarizer<ISSABasicBlock> s = new Summarizer<ISSABasicBlock>(
								appJar);

						for (String mDescr : methodDescriptors) {
							try {
								System.out.println(PRG + "Summarizing: "
										+ mDescr);
								s.summarize(mDescr,
										new TimedMonitor(TIME_LIMIT));
								System.out.println(PRG + "Summarized: "
										+ mDescr);
							} catch (CancelRuntimeException cre) {
								System.out.println(PRG
										+ "Summary time out for: " + mDescr);
								System.out.println(PRG + "   cause: "
										+ cre.getCause().toString());
							} catch (Exception e) {
								System.err.println(PRG
										+ "Could not summarize method: "
										+ mDescr);
								e.printStackTrace();
							}
						}
						store(pkg, s.serialize());
					} catch (Exception e) {
						System.err
								.println("Could not create summarizer for appJar: "
										+ appJar);
						e.printStackTrace();
					}
				}
			};

			futures.add(pool.submit(runner));
		}

		System.out.println("Joining on futures. "+futures.size()+" to wait on.");
		for (Future<?> f : futures) {
			try {
				System.out.print("Waiting...");
				f.get();
				System.out.println("...future joined.");
			} catch (InterruptedException e) {
				System.out.println("   Exception: "+e.getMessage());
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				System.out.println("   Exception: "+e.getMessage());
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			System.out.println("Are we exiting now, or looping more?");
		}
		pool.shutdown();
		System.out.println("Why are we still here?");
	}

	private static Set<String> loadLinesAsSet(String blacklistFile)
			throws IOException {
		if (blacklistFile == null) {
			return Sets.newHashSet();
		}
		List<String> lines = FileUtils.readLines(new File(blacklistFile));
		return Sets.newHashSet(lines);
	}

	private static void store(String pkg, String xml) {
		String pathname = OUTPUT_DIR + "/" + pkg + ".xml";
		File file = new File(pathname);
		try {
			FileUtils.writeStringToFile(file, xml);
			System.out.println("Wrote XML to: " + pathname);
		} catch (IOException e) {
			System.err.println("Could not write package xml file to: "
					+ pathname);
			e.printStackTrace();
		}
	}

	/**
	 * Calculate a map of package name to method descriptor for all interesting
	 * methods in the supplied jar file (which must also be on the classpath)
	 * 
	 * @param jarFile
	 * @return
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public static Multimap<String, String> getMethodsByPackage(String jarFile)
			throws ClassNotFoundException, IOException {
		Multimap<String, String> pkgMap = HashMultimap.create();
		List<String> classes = getClasses(jarFile);

		for (String c : classes) {
			// System.out.println(c);
			Method[] methods;
			Class<?> clazz;
			try {
				clazz = Class.forName(c);

				// skip a bunch of class types that don't really have code:
				if (clazz.isEnum() || clazz.isInterface()
						|| clazz.isAnnotation() || clazz.isAnonymousClass()
						|| clazz.isPrimitive() || clazz.isSynthetic()) {
					continue;
				}

				methods = clazz.getMethods();
			} catch (Throwable e) {
				System.out.println("Could not load class: " + c + " reason: "+ e.getMessage());
				//e.printStackTrace();
				continue;
			}
			for (Method m : methods) {
				// skip methods that are declared on other classes (eg:
				// java/lang/Object)
				if (!m.getDeclaringClass().equals(clazz)
				// skip bridge methods (what /are/ these?)
						|| m.isBridge()
						// skip synthetic methods (native?)
						|| m.isSynthetic()
						// skip varargs -- not sure summaries can support that.
						|| m.isVarArgs()) {
					continue;
				}

				StringBuilder desc = new StringBuilder(c);

				desc.append("." + m.getName());

				desc.append("(");
				for (Class<?> pType : m.getParameterTypes()) {
					desc.append(toDescStr(pType));
				}
				desc.append(")");

				desc.append(toDescStr(m.getReturnType()));

				String packageName = clazz.getPackage().getName();
				pkgMap.put(packageName, desc.toString());

			}
		}
		return pkgMap;
	}

	private static String toDescStr(Class<?> pType) {
		Map<Class<?>, String> primitives = Maps.newHashMap();
		primitives.put(Void.TYPE, "V");
		primitives.put(float.class, "F");
		primitives.put(double.class, "D");
		primitives.put(byte.class, "B");
		primitives.put(short.class, "S");
		primitives.put(int.class, "I");
		primitives.put(long.class, "J");
		primitives.put(boolean.class, "Z");
		primitives.put(char.class, "C");

		if (primitives.containsKey(pType)) {
			return (String) primitives.get(pType);
		}

		StringBuilder typeName = new StringBuilder();
		if (pType.isArray()) {
			typeName.append(pType.getName().replace('.', '/'));
		} else {
			typeName.append("L");
			typeName.append(pType.getName().replace('.', '/'));
			typeName.append(";");
		}
		return typeName.toString();
	}

	private static List<String> getClasses(String jarFile) throws IOException {
		List<String> classes = Lists.newArrayList();

		Multimap<String, String> packages = getPackages(jarFile);
		for (String pkg : packages.keySet()) {
			// System.out.println(pkg);
			for (String c : packages.get(pkg)) {
				classes.add(pkg + "." + c);
			}
		}
		return classes;
	}

	private static Multimap<String, String> getPackages(String appJar)
			throws IOException {
		Multimap<String, String> packages = HashMultimap.create();

		JarFile jf = new JarFile(appJar);
		Enumeration<JarEntry> entries = jf.entries();
		while (entries.hasMoreElements()) {
			JarEntry je = entries.nextElement();
			String name = je.getName();

			// skip everything but class files:
			if (!name.endsWith(".class")) {
				continue;
			}

			int slashIdx = name.lastIndexOf("/");
			String className = name.substring(slashIdx + 1,
					name.lastIndexOf('.'));
			String pkgName = name.substring(0, slashIdx);

			String dottedPkg = pkgName.replace('/', '.');

			packages.put(dottedPkg, className);
		}

		return packages;
	}
}
