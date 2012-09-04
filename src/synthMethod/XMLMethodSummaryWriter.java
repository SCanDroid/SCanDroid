package synthMethod;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.util.intset.OrdinalSet;

import domain.CodeElement;
import domain.FieldElement;
import domain.ReturnElement;
import flow.types.FieldFlow;
import flow.types.FlowType;
import flow.types.ParameterFlow;




public class XMLMethodSummaryWriter {
	public static String outputFile = "newMethodSummaries.xml";
	public static String outputPath = "data";

	//
	// Define XML element names
	//
	private final static String E_CLASSLOADER = "classloader";
	private final static String E_METHOD = "method";
	private final static String E_CLASS = "class";
	private final static String E_PACKAGE = "package";
	private final static String E_CALL = "call";
	private final static String E_NEW = "new";
	private final static String E_POISON = "poison";
	private final static String E_SUMMARY_SPEC = "summary-spec";
	private final static String E_RETURN = "return";
	private final static String E_PUTSTATIC = "putstatic";
	private final static String E_AASTORE = "aastore";
	private final static String E_PUTFIELD = "putfield";
	private final static String E_GETFIELD = "getfield";
	private final static String E_ATHROW = "throw";
	private final static String E_CONSTANT = "constant";

	//
	// Define XML attribute names
	//
	private final static String A_NAME = "name";
	private final static String A_TYPE = "type";
	private final static String A_CLASS = "class";
	private final static String A_SIZE = "size";
	private final static String A_DESCRIPTOR = "descriptor";
	private final static String A_REASON = "reason";
	private final static String A_LEVEL = "level";
	private final static String A_WILDCARD = "*";
	private final static String A_DEF = "def";
	private final static String A_STATIC = "static";
	private final static String A_VALUE = "value";
	private final static String A_FIELD = "field";
	private final static String A_FIELD_TYPE = "fieldType";
	private final static String A_ARG = "arg";
	private final static String A_ALLOCATABLE = "allocatable";
	private final static String A_REF = "ref";
	private final static String A_INDEX = "index";
	private final static String A_IGNORE = "ignore";
	private final static String A_FACTORY = "factory";
	private final static String A_NUM_ARGS = "numArgs";
	private final static String V_NULL = "null";
	private final static String V_TRUE = "true";
	  

	private static Element getElement(Document doc, XPathExpression e, Element parent, String child, String[] attrNs, String[] attrVs) throws XPathExpressionException {
		assert (attrNs.length == attrVs.length);
		Object result;
		result = e.evaluate(doc, XPathConstants.NODESET);

		NodeList myNL = (NodeList)result;
		Element myE;
		if (myNL.getLength() != 0) {
			assert(myNL.getLength() == 1);
			myE = (Element)myNL.item(0);
		}
		else {
			myE = doc.createElement(child);
			parent.appendChild(myE);
			for (int i = 0; i < attrNs.length; i++)
				myE.setAttribute(attrNs[i], attrVs[i]);
		}		
		return myE;
	}
	
	private static String addAttrP(String base, String childE, String attrN, String attrV) {
		return addToBase(base, childE, attrN, attrV)+"']";
	}
	
	private static String addToBase(String base, String childE, String attrN, String attrV) {
		return base+"/"+childE+"[@"+attrN+"='"+attrV;
	}
		
	
	private static Element getMethodElement(Document doc, XPath xpath, IMethod im, Element rootElement) throws XPathExpressionException {
		IClass myClass = im.getDeclaringClass();

		String classloadername = myClass.getReference().getClassLoader().getName().toString();
		String packagename = myClass.getName().getPackage().toString();		
		String classname = myClass.getName().getClassName().toString();
		String methodname = im.getName().toString();
		String descriptor = im.getDescriptor().toString();

		String expStr = addAttrP("/"+E_SUMMARY_SPEC, E_CLASSLOADER, A_NAME, classloadername);
		XPathExpression expr = xpath.compile(expStr);
		Element clE = getElement(doc, expr, rootElement, E_CLASSLOADER, new String[] {A_NAME}, new String[] {classloadername});
		
		expStr = addAttrP(expStr, E_PACKAGE, A_NAME, packagename);
		expr = xpath.compile(expStr);
		Element pE = getElement(doc, expr, clE, E_PACKAGE, new String[] {A_NAME}, new String[] {packagename});
		
		expStr = addAttrP(expStr, E_CLASS, A_NAME, classname);
		expr = xpath.compile(expStr);
		Element cE = getElement(doc, expr, pE, E_CLASS, new String[] {A_NAME}, new String[] {classname});			
		
		expStr = addToBase(expStr, E_PACKAGE, A_NAME, methodname)+"' and @"+A_DESCRIPTOR+"='"+descriptor+"']";
		expr = xpath.compile(expStr);
		Element mE = getElement(doc, expr, cE, E_METHOD, new String[] {A_NAME, A_DESCRIPTOR}, new String[] {methodname, descriptor});
		
		return mE;
		
	}
	
	public static void createXML() {
		try {
			if (MethodAnalysis.newSummaries.isEmpty())
				return;
			
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			Document doc = docBuilder.newDocument();
			XPath xpath = XPathFactory.newInstance().newXPath();

			// root elements
			Element rootElement = doc.createElement(E_SUMMARY_SPEC);
			doc.appendChild(rootElement);
						

			for (Entry<IMethod, Map<FlowType, Set<CodeElement>>> imE:MethodAnalysis.newSummaries.entrySet()) {
				IMethod im = imE.getKey();				
				Element mE = getMethodElement(doc, xpath, im, rootElement);
				if (im.isStatic())
					mE.setAttribute(A_STATIC, V_TRUE);
											
				for (Entry<FlowType, Set<CodeElement>> ftE:imE.getValue().entrySet()) {
					addFlowsToMethod(doc, mE, ftE, buildIKParamMap(im));
				}
				
			}
			
			
			Calendar cal = new GregorianCalendar();
//			cal.get(GregorianCalendar.DATE)
			// write the content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			//transformerFactory.setAttribute("indent-number", new Integer(4));
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			DOMSource source = new DOMSource(doc);
			
			StreamResult result = new StreamResult(new File("data"+File.separator+"MethodSummary-"+cal.getTime().toString().replace(' ', '_')+".xml"));
			transformer.transform(source, result);


		}
		catch (Exception e) {
			e.printStackTrace();			
		}
	}
	
	private static Map<InstanceKey, Set<Integer>> buildIKParamMap(IMethod im) {
		Map<InstanceKey, Set<Integer>> IKMap = new HashMap<InstanceKey, Set<Integer>>();
		for (Entry<Integer, OrdinalSet<InstanceKey>> entry:MethodAnalysis.methodTaints.get(im).entrySet()) {
			for (InstanceKey ik:entry.getValue()) {
				Set<Integer> intSet = IKMap.get(ik);
				if (intSet == null) {
					intSet = new HashSet<Integer> ();
					IKMap.put(ik, intSet);
				}
				intSet.add(entry.getKey());
			}			
		}
		return IKMap;
	}

	private static void addFlowsToMethod(Document doc, Element mE,
			Entry<FlowType, Set<CodeElement>> ftE, Map<InstanceKey, Set<Integer>> IKMap) {
		FlowType ft = ftE.getKey();
		if (ft instanceof ParameterFlow) {
			for (CodeElement ce: ftE.getValue()) {
				if (ce instanceof ReturnElement)
				{
					Element e = doc.createElement(E_RETURN);
					e.setAttribute(A_VALUE, "arg"+((ParameterFlow)ft).argNum);
					mE.appendChild(e);
				}
				else if (ce instanceof FieldElement) {
					FieldElement fe = (FieldElement)ce;
					if (IKMap.containsKey(fe.getIK())) {						
						for (Integer i:IKMap.get(fe.getIK())) {						
							Element e = doc.createElement(E_PUTFIELD);
							e.setAttribute(A_CLASS, fe.getRef().getDeclaringClass().getName().toString());
							e.setAttribute(A_FIELD, fe.getRef().getName().toString());
							e.setAttribute(A_FIELD_TYPE, fe.getRef().getFieldType().getName().toString());													
							e.setAttribute(A_REF, "arg"+i.intValue());
							e.setAttribute(A_VALUE, "arg"+((ParameterFlow)ft).argNum);
							mE.appendChild(e);
						}
					}
					else
						throw new IllegalArgumentException("FieldElement IK Not Found: " + fe.getIK()+" hash: " + fe.getIK().hashCode());			
				}
				else
					throw new IllegalArgumentException("Invalid CodeElement Type");
			}		
		}
		else if (ft instanceof FieldFlow) {
			Element e = doc.createElement(E_GETFIELD);
			FieldFlow ff = (FieldFlow)ft;
			e.setAttribute(A_CLASS, ff.getRef().getDeclaringClass().getName().toString());
			e.setAttribute(A_FIELD, ff.getRef().getName().toString());
			e.setAttribute(A_FIELD_TYPE, ff.getRef().getFieldType().getName().toString());
			String localDef = ff.getRef().getName().toString()+"_localDef";
			e.setAttribute(A_DEF, localDef);
			e.setAttribute(A_REF, "arg0");
			mE.appendChild(e);

			for (CodeElement ce: ftE.getValue()) {
				if (ce instanceof ReturnElement) {
					Element re = doc.createElement(E_RETURN);
					re.setAttribute(A_VALUE, localDef);
					mE.appendChild(re);
				}
				else if (ce instanceof FieldElement) {
					FieldElement fe = (FieldElement)ce;
					if (IKMap.containsKey(fe.getIK())) {						
						for (Integer i:IKMap.get(fe.getIK())) {
							Element pf = doc.createElement(E_PUTFIELD);
							pf.setAttribute(A_CLASS, fe.getRef().getDeclaringClass().getName().toString());
							pf.setAttribute(A_FIELD, fe.getRef().getName().toString());
							pf.setAttribute(A_FIELD_TYPE, fe.getRef().getFieldType().getName().toString());													
							pf.setAttribute(A_REF, "arg"+i.intValue());
							pf.setAttribute(A_VALUE, localDef);
							mE.appendChild(pf);
						}
					}
					else
						throw new IllegalArgumentException("FieldElement IK Not Found");
				}
				else
					throw new IllegalArgumentException("Invalid CodeElement Type");				
			}
		}
	
				

	}

	
	
}
