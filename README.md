SCanDroid
=========
Security Certiﬁer for anDroid

(c) Copyright 2009-2012

The preferred license for SCanDroid is the BSD License and the majority of the SCanDroid software is licensed with it.  However there are a few case-by-case exceptions that are under the Eclipse Public License.

Getting Started
===============
You will need to have [ant](http://ant.apache.org/) and [JDK 5 or 6](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JRE 7 is not supported by WALA at the moment) installed to compile SCanDroid.

Fetch SCanDroid from github
```
git clone https://github.com/SCanDroid/SCanDroid.git
```

####WALA
Download WALA and patch source
```
cd SCanDroid/wala
make
```
Import WALA into Eclipse

1. File => Import => Existing Projects into Workspace
2. Ensure that "copy projects into workspace" is __not__ checked
3. Browser to the SCanDroid/wala/wala-src directory, click ok
4. Various WALA projects should appear in the "Projects:" list
5. Uncheck the following WALA related projects:  _polyglot_ and _js_
6. Click Finish

Export the following WALA .jar files into SCanDroid/wala
- wala_cast.jar
- wala_cast_java.jar
- wala_cast_java_jdt.jar
- wala_core_tests.jar
- wala_core.jar
- wala_ide.jar
- wala_shrike.jar
- wala_util.jar

Modify your WALA properties file according to [WALA:Getting Started](http://wala.sourceforge.net/wiki/index.php/UserGuide:Getting_Started#Configuring_WALA_properties).  Specifically, you may need to change the **java_runtime_dir** property to your JRE path.  You may need to modify one of the following files depending on your OS.
- SCanDroid/conf/wala.properties.linux
- SCanDroid/conf/wala.properties.mac
- SCanDroid/conf/wala.properties.windows

####Dependencies
Place other dependencies in _SCanDroid/lib_.  Your dependency files should be similar to the following: 

`dexlib-1.3.4-dev.jar`, `guava-12.0.1.jar`, `jgrapht-0.8.3.jar`, `junit-4.9b2.jar`, `commons-cli-1.2.jar`.

If you downloaded a different version, please edit build.xml and change the .jar names accordingly.

Finally, SCanDroid uses the Android library during the analysis.  The Android jar included in the [Android SDK](developer.android.com/sdk) includes methods that are stubbed out.  This makes it lightweight and ideal for download and development, however SCanDroid requires either a well modeled Android library or the full implementation.  You may model your own, compile the full implemntation, or download a precompiled version online. [GrepCode](http://grepcode.com/project/repository.grepcode.com/java/ext/com.google.android/android/) has some precompiled Android libraries that may be used.

####Compiling and Running
Compiling SCanDroid
```
ant clean; ant build; ant jar
```
Running SCanDroid
```
java -jar sap.jar --help
#for a list of options
java -Xmx6g -jar sap.jar --android-lib=android-2.3.7_r1.jar application.apk
#Example: Sets the Java VM maximum memory allocation pool to 6g, includes 
#version 2.3.7_r1 of the android library in the scope of the analysis, and 
#starts analyzing application.apk
```


Dependencies
============
- [WALA](http://wala.sourceforge.net) provides static analysis capabilities for Java bytecode and related languages.  The system is licensed under the Eclipse Public License.
- [JUnit](http://www.junit.org) is a unit testing framework. You need JUnit only if you want to run the unit tests.  JUnit is licensed under the terms of the IBM Common Public License. `4.9b2`
- [JGraphT](http://jgrapht.org) is a free Java class library that provides mathematical graph-theory objects and algorithms. It runs on Java 2 Platform (requires JDK 1.6 or later). JGraphT is licensed under the terms of the GNU Lesser General Public License (LGPL). `0.8.3`
- [Apache Commons CLI](http://commons.apache.org/cli) provides an API for parsing command line options passed to programs. The Commons CLI library is licensed under the Apache Software License. `1.2`
- [dexlib](http://code.google.com/p/smali) is a library to read in and write out dex files. dexlib is licensed under the BSD License. `1.3.4`
    - [Guava](http://code.google.com/p/guava-libraries/) contains several of Google's core libraries. A dependency used by dexlib and is under the Apache License. `12.0.1`
