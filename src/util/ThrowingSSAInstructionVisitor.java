package util;

import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction.IVisitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;

public class ThrowingSSAInstructionVisitor implements IVisitor {
	private final RuntimeException e;
	
	public ThrowingSSAInstructionVisitor(RuntimeException e) {
		this.e = e;
	}
	
	@Override
	public void visitGoto(SSAGotoInstruction instruction) {
		throw e;
	}

	@Override
	public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
		throw e;

	}

	@Override
	public void visitArrayStore(SSAArrayStoreInstruction instruction) {
		throw e;

	}

	@Override
	public void visitBinaryOp(SSABinaryOpInstruction instruction) {
		throw e;

	}

	@Override
	public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
		throw e;

	}

	@Override
	public void visitConversion(SSAConversionInstruction instruction) {
		throw e;

	}

	@Override
	public void visitComparison(SSAComparisonInstruction instruction) {
		throw e;

	}

	@Override
	public void visitConditionalBranch(
			SSAConditionalBranchInstruction instruction) {
		throw e;

	}

	@Override
	public void visitSwitch(SSASwitchInstruction instruction) {
		throw e;

	}

	@Override
	public void visitReturn(SSAReturnInstruction instruction) {
		throw e;

	}

	@Override
	public void visitGet(SSAGetInstruction instruction) {
		throw e;

	}

	@Override
	public void visitPut(SSAPutInstruction instruction) {
		throw e;

	}

	@Override
	public void visitInvoke(SSAInvokeInstruction instruction) {
		throw e;

	}

	@Override
	public void visitNew(SSANewInstruction instruction) {
		throw e;

	}

	@Override
	public void visitArrayLength(SSAArrayLengthInstruction instruction) {
		throw e;

	}

	@Override
	public void visitThrow(SSAThrowInstruction instruction) {
		throw e;

	}

	@Override
	public void visitMonitor(SSAMonitorInstruction instruction) {
		throw e;

	}

	@Override
	public void visitCheckCast(SSACheckCastInstruction instruction) {
		throw e;

	}

	@Override
	public void visitInstanceof(SSAInstanceofInstruction instruction) {
		throw e;

	}

	@Override
	public void visitPhi(SSAPhiInstruction instruction) {
		throw e;

	}

	@Override
	public void visitPi(SSAPiInstruction instruction) {
		throw e;

	}

	@Override
	public void visitGetCaughtException(
			SSAGetCaughtExceptionInstruction instruction) {
		throw e;

	}

	@Override
	public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
		throw e;

	}

}
