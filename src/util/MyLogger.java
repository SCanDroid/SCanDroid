/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Adam Fuchs          <afuchs@cs.umd.edu>
 *  Avik Chaudhuri      <avik@cs.umd.edu>
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

package util;

import static java.lang.System.getProperty;
import static util.MyLogger.LogLevel.WARNING;

import com.ibm.wala.util.warnings.Warning;

public final class MyLogger {
    private static final int logLevel = Integer.valueOf(getProperty("LOG_LEVEL") == null ? "0" : getProperty("LOG_LEVEL"));

    public static enum LogLevel {
         ERROR (0), WARNING (1), INFO (2), DEBUG (3);
         private final int level;

         private LogLevel(int i) {
             level=i;
         }

         public int getLevel(){
             return level;
         }
    }

    public static void log(LogLevel level, String msg) {
        if (logLevel >= level.getLevel()){
            System.out.println("[" + level + "] " + msg);
        }
    }

    public static void log(LogLevel level, String msg, Exception exc) {
        log(level,msg + " \n >>"+ exc.getMessage());
    }

    public static void log(Warning w) {
        log(WARNING, "[" + w.getLevel() + "] -- " + w.getMsg());
    }

}
