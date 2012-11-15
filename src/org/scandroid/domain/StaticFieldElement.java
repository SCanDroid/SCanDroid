package org.scandroid.domain;

import com.ibm.wala.types.FieldReference;

public class StaticFieldElement extends CodeElement {
	private final FieldReference fieldRef;
	
	public StaticFieldElement(FieldReference fieldRef) {
		this.fieldRef = fieldRef;
	}
	
    public FieldReference getRef() {
    	return fieldRef;
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((fieldRef == null) ? 0 : fieldRef.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StaticFieldElement other = (StaticFieldElement) obj;
		if (fieldRef == null) {
			if (other.fieldRef != null)
				return false;
		} else if (!fieldRef.equals(other.fieldRef))
			return false;
		return true;
	}
}
