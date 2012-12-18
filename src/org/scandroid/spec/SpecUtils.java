package org.scandroid.spec;

import java.lang.reflect.Array;

public class SpecUtils {

	/**
	 * Combine two specs objects.
	 * 
	 * @param s1
	 * @param s2
	 * @return
	 */
	public static ISpecs combine(final ISpecs s1, final ISpecs s2) {
		return new ISpecs() {
			@Override
			public SourceSpec[] getSourceSpecs() {
				SourceSpec[] s1Sources = s1.getSourceSpecs();
				SourceSpec[] s2Sources = s2.getSourceSpecs();
				
				return concat(s1Sources, s2Sources);
			}
			
			@Override
			public SinkSpec[] getSinkSpecs() {
				return concat(s1.getSinkSpecs(), s2.getSinkSpecs());
			}
			
			@Override
			public MethodNamePattern[] getEntrypointSpecs() {
				return concat(s1.getEntrypointSpecs(), s2.getEntrypointSpecs());
			}
	
			@SuppressWarnings("unchecked")
			private <T> T[] concat(final T[] a, final T[] b) {
				if (null == a) {
					return b;
				}
				if (null == b) {
					return a;
				}
				
				T[] newArray = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
				System.arraycopy(a, 0, newArray, 0, a.length);
				System.arraycopy(b, 0, newArray, a.length, b.length);
				return newArray;
			}
		};
	}

}
