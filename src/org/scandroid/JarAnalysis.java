/**
 * 
 */
package org.scandroid;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * @author creswick
 *
 */
public class JarAnalysis {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 */
	public static void main(String[] args) throws IOException, ClassNotFoundException {

		List<String> classes = getClasses(args[0]);
				
		for (String c : classes ) {
			System.out.println(c);
			
			Class<?> clazz = Class.forName(c);
			
			// skip a bunch of class types that don't really have code:
			if (clazz.isEnum() 
			    || clazz.isInterface() 
			    || clazz.isAnnotation() 
				|| clazz.isAnonymousClass()
				|| clazz.isPrimitive()
				|| clazz.isSynthetic()
				) {
				continue;
			}
			
			Method[] methods = clazz.getMethods();
			
			for (Method m : methods) {				
				// skip methods that are declared on other classes (eg: java/lang/Object)
				if ( !m.getDeclaringClass().equals(clazz)
						// skip bridge methods (what /are/ these?)
						|| m.isBridge()
						// skip synthetic methods (native?)
						|| m.isSynthetic()
						// skip varargs -- not sure summaries can support that.
						|| m.isVarArgs()) {
					continue;
				}

				StringBuilder desc = new StringBuilder(c);

				desc.append("."+m.getName());
				
				desc.append("(");
				for( Class pType : m.getParameterTypes() ) {
					desc.append(toDescStr(pType));
				}
				desc.append(")");
				
				desc.append(toDescStr(m.getReturnType()));
			
				System.out.println("   "+desc.toString());

			}
		}
	}
	
	private static String toDescStr(Class pType) {
		Map<Class, String> primitives = Maps.newHashMap(); 
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
			return (String)primitives.get(pType);
		}
	
		StringBuilder typeName = new StringBuilder();
		if (pType.isArray()){
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
		for(String pkg : packages.keySet() ){
			//System.out.println(pkg);
			for (String c : packages.get(pkg)) {
				classes.add(pkg+"."+c);
			}
		}
		return classes;
	}

	private static Multimap<String, String> getPackages(String appJar) throws IOException {
		Multimap<String, String> packages = HashMultimap.create();
		
		JarFile jf = new JarFile(appJar);
		Enumeration<JarEntry> entries = jf.entries();
		while (entries.hasMoreElements()) {
			JarEntry je = entries.nextElement();
			String name = je.getName();
			
			// skip everything but class files:
			if ( !name.endsWith(".class") ) {
				continue;
			}
			
			int slashIdx = name.lastIndexOf("/");
			String className = name.substring(slashIdx + 1, name.lastIndexOf('.'));
			String pkgName = name.substring(0, slashIdx);
			
			String dottedPkg = pkgName.replace('/', '.');

			
			packages.put(dottedPkg, className);
		}
		
		return packages;
	}
}
