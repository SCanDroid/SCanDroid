/*
*
* Copyright (c) 2009-2012,
*
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


package flow.types;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/** A flow to or from a field. The associated block represents
 * either the location of the get or put instruction, or the
 * entry or exit block of a method which is reading or writing
 * the field. In the former case, the associate field is redundant,
 * but potentially convenient. In the latter case, it's necessary.
 *
 * @author atomb
 *
 * @param <E>
 */
public class FieldFlow <E extends ISSABasicBlock> implements FlowType {

   private final BasicBlockInContext<E> block;
   private final IField field;
   private final boolean source;
   
   public FieldFlow(BasicBlockInContext<E> block, IField field, boolean source)
   {
       this.block = block;
       this.field = field;
       this.source = source;
   }

   @Override
   public int hashCode() {
       final int prime = 31;
       int result = 1;
       result = prime * result + ((block == null) ? 0 : block.hashCode());
       result = prime * result + ((field == null) ? 0 : field.hashCode());
       result = prime * result + (source ? 1231 : 1237);
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
       FieldFlow other = (FieldFlow) obj;
       if (block == null) {
           if (other.block != null)
               return false;
       } else if (!block.equals(other.block))
           return false;
       if (field == null) {
           if (other.field != null)
               return false;
       } else if (!field.equals(other.field))
           return false;
       if (source != other.source)
           return false;
       return true;
   }

   @Override
   public String toString()
   {
       if(block != null)
           return "FieldFlow("+block.toString()+")";
       else
           return "FieldFlow("+field.toString()+")";
   }

   @Override
   public BasicBlockInContext<E> getBlock()
   {
       return block;
   }
   
   public IField getField() {
       return field;
   }

   @Override
   public boolean isSource() {
       return source;
   }
}
