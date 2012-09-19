package synthMethod;

import spec.MethodNamePattern;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.strings.Atom;

public class MethodPredicates {
	
	@SuppressWarnings("unchecked")
	public static final Predicate<IMethod> every = Predicate.TRUE;

	/**
	 * Select methods with the supplied name.
	 * 
	 * @param name
	 * @return
	 */
	public static Predicate<IMethod> isNamed(final String name) {
		return new Predicate<IMethod>() {
			@Override
			public boolean test(IMethod t) {
				String methodName = atomToStr(t.getName());
				return name.equals(methodName);
			}
		};
	}

	public static Predicate<IMethod> matchesPattern(
			final MethodNamePattern p) {
		
		Predicate<IMethod> nameMatches = isNamed(p.getMemberName());
		
		Predicate<IMethod> classMatches = fromClass(p.getClassName());
		
		return nameMatches.and(classMatches);
	}

	private static Predicate<IMethod> fromClass(final String needleClassName) {
		return new Predicate<IMethod>() {
			@Override
			public boolean test(IMethod t) {
				TypeName typeName = t.getDeclaringClass().getName();
				String testClassName = atomToStr(typeName.getClassName());
				String testPkgName = atomToStr(typeName.getPackage());
				String fullClassName = "L"+testPkgName + "/" + testClassName;
				boolean matchFound = needleClassName.equals(fullClassName);
				
				System.out.println("Comparing class: "+fullClassName+"\n"+
						"with: "+needleClassName);
				System.out.println(" matches? "+matchFound);
				
				return matchFound;
			}
		};
	}

	protected static String atomToStr(Atom atom) {
		return new String(atom.getValArray());
	}
}
