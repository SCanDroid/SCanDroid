package org.scandroid.testing;

public class ConstructorArgFlow {
	public static class Id {
		Object o;
		
		public Id() {
			
		}

		public Id(Object o) {
			this.o = o;
		}
		
		public void setO(Object o) {
			this.o = o;
		}

		public Object getO() {
			return this.o;
		}
	}

	public static Id flow(String s) {
		return new Id(s);
	}
	
	public static Id flow2(String s) {
		Id id = new Id(s);
		return id;
	}
	
	public static Id manualFlow(String s) {
		// TODO: refactor compilation of flowtypes such that
		// if RHS flowtype SSA val chain does *not* include SSA val of LHS,
		// then we emit some SSA instruction to connect the two 
		Id id = new Id();
		id.setO(s);
		return id;
	}
	
	public static Object fieldAccessFlow(Id id) {
		// TODO: bug in argument of return
		return id.o;
	}
	
	public static Object getterAccessFlow(Id id) {
		// TODO: correct emission of call tag will fix this
		return id.getO();
	}
}
