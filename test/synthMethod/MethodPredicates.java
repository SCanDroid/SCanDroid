package synthMethod;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.util.Predicate;

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
				String methodName = new String(t.getName().getValArray());
				
				return name.equals(methodName);
			}
		};
	}

}
