/*
 *
 * Copyright (c) 2010-2012,
 *
 *  Jonathan Bardin     <astrosus@gmail.com>
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

package com.ibm.wala.classLoader;

import static com.ibm.wala.types.TypeName.string2TypeName;
import static util.MyLogger.LogLevel.DEBUG;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;


import util.MyLogger;

import com.ibm.wala.ipa.callgraph.impl.SetOfClasses;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.shrike.ShrikeClassReaderHandle;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

/**
 * ClassLoader for Java & Dalvik.
 *
 */
public class WDexClassLoaderImpl extends ClassLoaderImpl {
    private SetOfClasses lExclusions;
    private IClassLoader lParent;

    public WDexClassLoaderImpl(ClassLoaderReference loader,
            ArrayClassLoader arrayClassLoader, IClassLoader parent,
            SetOfClasses exclusions, IClassHierarchy cha) {
        super(loader, arrayClassLoader, parent, exclusions, cha);
        lParent = parent;
        lExclusions = exclusions;
        //DEBUG_LEVEL = 0;
    }
    
    /*
     * (non-Javadoc)
     *
     * @see
     * com.ibm.wala.classLoader.ClassLoaderImpl#loadAllClasses(java.util.Collection
     * )
     */
    @Override
    protected void loadAllClasses(Collection<ModuleEntry> moduleEntries, Map<String, Object> fileContents) {

    	for (Iterator<ModuleEntry> it = moduleEntries.iterator(); it.hasNext();) {
    		ModuleEntry entry = it.next();

    		// Dalvik class
    		if (entry instanceof DexModuleEntry) {
    			DexModuleEntry dexEntry = ((DexModuleEntry) entry);
    			TypeName tName = string2TypeName(dexEntry.getClassName());

    			//if (DEBUG_LEVEL > 0) {
    			//  System.err.println("Consider dex class: " + tName);
    			//}

    			//System.out.println("Typename: " + tName.toString());
    			//System.out.println(tName.getClassName());
    			if (loadedClasses.get(tName) != null) {
    				Warnings.add(MultipleDexImplementationsWarning
    						.create(dexEntry.getClassName()));
    			} else if (lParent != null && lParent.lookupClass(tName) != null) {
    				Warnings.add(MultipleDexImplementationsWarning
    						.create(dexEntry.getClassName()));
    			}
    			//if the class is empty, ie an interface
    			//                  else if (dexEntry.getClassDefItem().getClassData() == null) {
    			//                      System.out.println("Jumping over (classdata null): "+dexEntry.getClassName());
    			//                      Warnings.add(MultipleDexImplementationsWarning
    			//                              .create(dexEntry.getClassName()));
    			//                  }
    			else {
    				IClass iClass = new DexIClass(this, cha, dexEntry);
    				if (iClass.getReference().getName().equals(tName)) {
    					MyLogger.log(DEBUG, "Load class: " + dexEntry.getClassName());
    					loadedClasses.put(tName, iClass);
    				} else {
    					Warnings.add(InvalidDexFile.create(dexEntry.getClassName()));
    				}
    			}





    		}

    		// Java Class
    		else if (entry instanceof JarFileEntry) {
    			String className = entry.getClassName().replace('.', '/');
    			//if (DEBUG_LEVEL > 0) {
    			//  System.err.println("Consider " + className);
    			//}

    			if (lExclusions != null && lExclusions.contains(className)) {
    				//if (DEBUG_LEVEL > 0) {
    				//  System.err.println("Excluding " + className);
    				//}
    				continue;
    			}

    			ShrikeClassReaderHandle reader = new ShrikeClassReaderHandle(
    					entry);

    			className = "L" + className;
    			//if (DEBUG_LEVEL > 0) {
    			//  System.err.println("Load class " + className);
    			//}
    			try {
    				TypeName T = TypeName.string2TypeName(className);
    				if (loadedClasses.get(T) != null) {
    					Warnings.add(MultipleDexImplementationsWarning
    							.create(className));
    				} else if (lParent != null && lParent.lookupClass(T) != null) {
    					Warnings.add(MultipleDexImplementationsWarning
    							.create(className));
    				} else {
    					ShrikeClass klass = new ShrikeClass(reader, this, cha);
    					if (klass.getReference().getName().equals(T)) {
    						loadedClasses.put(T, klass); // new ShrikeClass(reader, this, cha));
    						//if (DEBUG_LEVEL > 1) {
    						//  System.err.println("put " + T + " ");
    						//}
    					} else {
    						Warnings.add(InvalidDexFile.create(className));
    					}
    				}
    			} catch (InvalidClassFileException e) {
    				//if (DEBUG_LEVEL > 0) {
    				//  System.err.println("Ignoring class " + className
    				//          + " due to InvalidClassFileException");
    				//}
    				Warnings.add(InvalidDexFile.create(className));
    			}
    		} // continue if jar
    	}

    }

    /**
     * @return the IClassHierarchy of this classLoader.
     */
    public IClassHierarchy getClassHierarcy() {
        return cha;
    }

  /**
   * A warning when we find more than one implementation of a given class name
   */
  private static class MultipleDexImplementationsWarning extends Warning {

    final String className;

    MultipleDexImplementationsWarning(String className) {
      super(Warning.SEVERE);
      this.className = className;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + className;
    }

    public static MultipleDexImplementationsWarning create(String className) {
      return new MultipleDexImplementationsWarning(className);
    }
  }

  /**
   * A warning when we encounter InvalidClassFileException
   */
  private static class InvalidDexFile extends Warning {

    final String className;

    InvalidDexFile(String className) {
      super(Warning.SEVERE);
      this.className = className;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + className;
    }
    public static InvalidDexFile create(String className) {
      return new InvalidDexFile(className);
    }
  }
}
