package synthMethod;

import java.io.UTFDataFormatException;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import com.ibm.wala.ssa.SSAInstruction;
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
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

public class SSAtoXMLVisitor implements SSAInstruction.IVisitor {

    /**
     * A counter to use for generating unique local definition names.
     */
    private int defCounter = 0;

    /**
     * Map the known defNum to local def names.
     */
    private Map<Integer, String> localDefs = Maps.newHashMap();

    /**
     * XML document to use for creating elements.
     */
    private final Document doc;

    /**
     * XML elements that represent the ssa instructions
     */
    private final List<Element> summary = Lists.newArrayList();

    public SSAtoXMLVisitor(Document doc) {
        this.doc = doc;
    }

    @Override
    public void visitGoto(SSAGotoInstruction instruction) {
        // TODO Auto-generated method stub
    }

    @Override
    public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitArrayStore(SSAArrayStoreInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitBinaryOp(SSABinaryOpInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitConversion(SSAConversionInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitComparison(SSAComparisonInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitConditionalBranch(
            SSAConditionalBranchInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitSwitch(SSASwitchInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitReturn(SSAReturnInstruction instruction) {
        try {
            String localName = getLocalName(instruction.getDef());

            Element elt = doc.createElement(XMLSummaryWriter.E_RETURN);
            elt.setAttribute(XMLSummaryWriter.A_VALUE, localName);
            summary.add(elt);
        } catch (Exception e) {
            throw new SSASerializationException(e);
        }
    }

    /**
     * eg:
     * 
     * <getfield class="Ljava/lang/Thread" field="runnable"
     * fieldType="Ljava/lang/Runnable" def="x" ref="arg0" />
     * 
     * I think the get statics look like this:
     * 
     * <getstatic class="Ljava/lang/Thread" field="runnable"
     * fieldType="Ljava/lang/Runnable" def="x" />
     */
    @Override
    public void visitGet(SSAGetInstruction instruction) {
        try {
            String eltName;

            // TODO I suspect this is the wrong boolean to check:
            if (instruction.isStatic()) {
                eltName = XMLSummaryWriter.E_GETSTATIC;
            } else {
                eltName = XMLSummaryWriter.E_GETFIELD;
            }
            Element elt = doc.createElement(eltName);

            // TODO see above... same question about isStatic()
            if (!instruction.isStatic()) {
                String refName = getRefName(instruction.getRef());
                elt.setAttribute(XMLSummaryWriter.A_REF, refName);
            }

            String def = getLocalName(instruction.getDef());
            TypeReference fieldType = instruction.getDeclaredFieldType();
            TypeReference classType = instruction.getDeclaredField()
                    .getDeclaringClass();

            String fieldName = instruction.getDeclaredField().getName()
                    .toUnicodeString();

            elt.setAttribute(XMLSummaryWriter.A_CLASS, typeRefToStr(classType));
            elt.setAttribute(XMLSummaryWriter.A_FIELD, fieldName);
            elt.setAttribute(XMLSummaryWriter.A_FIELD_TYPE,
                    typeRefToStr(fieldType));
            elt.setAttribute(XMLSummaryWriter.A_DEF, def);

            summary.add(elt);
        } catch (Exception e) {
            throw new SSASerializationException(e);
        }
    }

    /**
     * <putstatic class="Ljava/lang/System" field="security"
     * fieldType="Ljava/lang/SecurityManager" value="secure" />
     * 
     * <putfield class="Ljava/lang/Thread" field="runnable"
     * fieldType="Ljava/lang/Runnable" ref="arg0" value="arg0" />
     * 
     */
    @Override
    public void visitPut(SSAPutInstruction instruction) {
        try {
            String eltName;

            // TODO I suspect this is the wrong boolean to check:
            if (instruction.isStatic()) {
                eltName = XMLSummaryWriter.E_PUTSTATIC;
            } else {
                eltName = XMLSummaryWriter.E_PUTFIELD;
            }
            Element elt = doc.createElement(eltName);

            // TODO see above... same question about isStatic()
            if (!instruction.isStatic()) {
                String refName = getRefName(instruction.getRef());
                elt.setAttribute(XMLSummaryWriter.A_REF, refName);
            }

            String value = getLocalName(instruction.getVal());
            TypeReference fieldType = instruction.getDeclaredFieldType();
            TypeReference classType = instruction.getDeclaredField()
                    .getDeclaringClass();

            String fieldName = instruction.getDeclaredField().getName()
                    .toUnicodeString();

            elt.setAttribute(XMLSummaryWriter.A_CLASS, typeRefToStr(classType));
            elt.setAttribute(XMLSummaryWriter.A_FIELD, fieldName);
            elt.setAttribute(XMLSummaryWriter.A_FIELD_TYPE,
                    typeRefToStr(fieldType));
            elt.setAttribute(XMLSummaryWriter.A_VALUE, value);

            summary.add(elt);
        } catch (Exception e) {
            throw new SSASerializationException(e);
        }

    }

    @Override
    public void visitInvoke(SSAInvokeInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitNew(SSANewInstruction instruction) {
        try {
            int defNum = instruction.getDef();
            String localName = newLocalDef(defNum);

            TypeReference type = instruction.getConcreteType();
            String className = type.getName().getClassName().toUnicodeString();

            Element elt = doc.createElement(XMLSummaryWriter.E_NEW);
            elt.setAttribute(XMLSummaryWriter.A_DEF, localName);
            elt.setAttribute(XMLSummaryWriter.A_CLASS, className);
            summary.add(elt);
        } catch (Exception e) {
            throw new SSASerializationException(e);
        }
    }

    @Override
    public void visitArrayLength(SSAArrayLengthInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitThrow(SSAThrowInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitMonitor(SSAMonitorInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitCheckCast(SSACheckCastInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitInstanceof(SSAInstanceofInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitPhi(SSAPhiInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitPi(SSAPiInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitGetCaughtException(
            SSAGetCaughtExceptionInstruction instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
        // TODO Auto-generated method stub

    }

    /**
     * Add a new defNum, creating a name for that defnum.
     * 
     * @param defNum
     */
    private String newLocalDef(int defNum) {
        String newName = "localdef_" + defCounter;
        localDefs.put(defNum, newName);
        defCounter++;

        return newName;
    }

    /**
     * Get a local name for the provided defNum.
     * 
     * If, for some reason, the defNum has not yet been seen (and, thus, has no
     * local name associated with it) then this will throw an illegal state
     * exception.
     * 
     * @param defNum
     * @return
     * @throws IllegalStateException
     */
    private String getLocalName(int defNum) throws IllegalStateException {
        if (localDefs.containsKey(defNum)) {
            return localDefs.get(defNum);
        }
        throw new IllegalStateException("defNum: " + defNum
                + " is not defined.");
    }

    /**
     * Calculate the correct ref name based on the incomming number.
     * 
     * Note that this needs to be consistent with XMLMethodSummaryReader's use
     * of the symbolTable in that class.
     * 
     * @param ref
     * @return
     */
    private String getRefName(int ref) {
        if (0 == ref) {
            return "unknown";
        } else {
            return "arg" + (ref - 1);
        }
    }

    public List<Element> getInstSummary() {
        return summary;
    }

    private String typeRefToStr(TypeReference fieldType)
            throws UTFDataFormatException {
        Atom className = fieldType.getName().getClassName();
        Atom pkgName = fieldType.getName().getPackage();
        return pkgName.toUnicodeString() + "/" + className.toUnicodeString();
    }

}
