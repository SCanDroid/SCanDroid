/**
 * 
 */
package org.scandroid.flow.types;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;

/**
 * @author acfoltzer
 *
 */
public class StaticFieldFlow <E extends ISSABasicBlock> extends FlowType<E> {
	private final IField field;
	
	public StaticFieldFlow(BasicBlockInContext<E> block, IField field, boolean source) {
		super(block, source);
		this.field = field;
	}
	
    @Override
    public String toString() {
        return "StaticFieldFlow( field=" + field + " "+ super.toString() + ")";
    }

    @Override
    public String descString() {
        return field.getDeclaringClass().toString() + "." + field.getName().toString();
    }
    
    public IField getField() {
		return field;
	}

	/* (non-Javadoc)
	 * @see org.scandroid.flow.types.FlowType#visit(org.scandroid.flow.types.FlowType.FlowTypeVisitor)
	 */
	@Override
	public <R> R visit(FlowTypeVisitor<E, R> v) {
		return v.visitStaticFieldFlow(this);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((field == null) ? 0 : field.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("unchecked")
		StaticFieldFlow<E> other = (StaticFieldFlow<E>) obj;
		if (field == null) {
			if (other.field != null)
				return false;
		} else if (!field.equals(other.field))
			return false;
		return true;
	}

}
