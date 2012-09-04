package util;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.ClassLoaderReference;

public class LoaderUtils {
    
    public static boolean fromLoader(CGNode node, ClassLoaderReference clr) {
        IClass declClass = node.getMethod().getDeclaringClass();

        ClassLoaderReference nodeClRef =
                declClass.getClassLoader().getReference();

        return nodeClRef.equals(clr);
    }
    
    public static boolean fromLoader(IMethod method, ClassLoaderReference clr) {
        IClass declClass = method.getDeclaringClass();

        ClassLoaderReference nodeClRef =
                declClass.getClassLoader().getReference();

        return nodeClRef.equals(clr);
    }
}
