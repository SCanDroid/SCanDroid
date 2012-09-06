/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>)
 *  Steve Suh           <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */

package spec;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.strings.Atom;

public class MethodNamePattern {
    final String className;

    final String memberName;

    final String descriptor;  // null = match any types

    public MethodNamePattern(String c, String m, String d) {
        className = c;
        memberName = m;
        descriptor = d;
    }

    public MethodNamePattern(String c, String m) {
        className = c;
        memberName = m;
        descriptor = null;
    }

    private Collection<IMethod> lookupMethods(IClass c) {
        Collection<IMethod> matching = new LinkedList<IMethod>();
        Atom atom = Atom.findOrCreateUnicodeAtom(memberName);
        Descriptor desc = descriptor == null ? null : Descriptor.findOrCreateUTF8(descriptor);
        Collection<IMethod> allMethods = c.getAllMethods();
        for(IMethod m: allMethods) {
            if(m.getName().equals(atom) && (desc == null || m.getDescriptor().equals(desc))) {
                matching.add(m);
            }
        }
        return matching;
    }

    public Collection<IMethod> getPossibleTargets(IClassHierarchy cha) {
    	Collection<IMethod> matching = new LinkedList<IMethod>();
    	IClass c;
    	c = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, className));
    	if (c != null)
    		matching.addAll(lookupMethods(c));
    	c = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, className));
    	if (c != null)
    		matching.addAll(lookupMethods(c));
    	c = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Extension, className));
    	if (c != null)
    		matching.addAll(lookupMethods(c));

        
        Set<IMethod> targets = HashSetFactory.make();
        for(IMethod im:matching) {
        	targets.addAll(cha.getPossibleTargets(im.getReference()));
        }
        return targets;
    }
}
