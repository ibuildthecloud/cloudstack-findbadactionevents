/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

public class FindCallers extends EmptyVisitor {

    String annotation;
    String className;
    String baseClass;
    Annotated currentMethod;
    List<Annotated> annotated = new ArrayList<FindCallers.Annotated>();
    Map<String, List<Annotated>> annotatedMap = new HashMap<String, List<Annotated>>();
    List<Invocation> invocations = new ArrayList<FindCallers.Invocation>();
    
    public FindCallers(String annotation) {
        super();
        this.annotation = annotation;
    }

    @Override
    public void visit(int arg0, int arg1, String arg2, String arg3, String arg4, String[] arg5) {
        this.className = arg2;
        this.baseClass = arg4;
        super.visit(arg0, arg1, arg2, arg3, arg4, arg5);
    }

    @Override
    public MethodVisitor visitMethod(int arg0, String arg1, String arg2, String arg3, String[] arg4) {
        if ( ( arg0 & Opcodes.ACC_BRIDGE ) == Opcodes.ACC_BRIDGE &&
                ( arg0 & Opcodes.ACC_SYNTHETIC ) == Opcodes.ACC_SYNTHETIC ){
            return null;
        }
        
        this.currentMethod = new Annotated(className, baseClass, arg1, arg2,
                ( arg0 & Opcodes.ACC_PUBLIC ) == Opcodes.ACC_PUBLIC );
        return super.visitMethod(arg0, arg1, arg2, arg3, arg4);
    }

    @Override
    public void visitMethodInsn(int arg0, String arg1, String arg2, String arg3) {
        invocations.add(new Invocation(className, baseClass, currentMethod.name, arg2, arg3));
        super.visitMethodInsn(arg0, arg1, arg2, arg3);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String arg0, boolean arg1) {
        if ( arg0.equals(this.annotation) ) {
            this.annotated.add(currentMethod);
            
            String key = currentMethod.getTarget();
            List<Annotated> annotated = annotatedMap.get(key);
            if ( annotated == null ) {
                annotated = new ArrayList<FindCallers.Annotated>();
                annotatedMap.put(key, annotated);
                annotated.add(currentMethod);
            }
        }
        return super.visitAnnotation(arg0, arg1);
    }

    public static void main(String... args) {
        
        OutputStream os = null;
        
        try {
            FindAnnotationWalker w = new FindAnnotationWalker();
            
            FindCallers cl = w.walk(args[0], args[1]);
            
            for ( Annotated a : cl.annotated )
                System.out.println(a);
            
            System.out.println("Start CSV");
            
            os = new FileOutputStream(args[2]);
            
            for ( Annotated a : cl.annotated ) {
                if ( ! a.isPublic ) {
                    String line = String.format("%s\t%s\t%s\t%s\t%s\t%s\n", "no",
                            "",
                            "",
                            a.className,
                            a.name,
                            a.getTarget());
                    
                    os.write(line.getBytes("UTF-8"));
                }
            }
            
            for ( Invocation i : cl.invocations ) {
                if ( cl.annotatedMap.containsKey(i.getTarget()) ) {
                    for ( Annotated a : cl.annotatedMap.get(i.getTarget()) ) {
                        String ok = "";
                        
                        if ( i.sourceClassName.endsWith("Cmd") 
                                || i.sourceClassName.endsWith("Test")
                                || i.sourceClassName.contains("/test/") ) {
                            ok = "ok";
                        }
                        
                        if ( i.sourceClassName.equals(a.className) ) {
                            ok = "no";
                        }
                        
                        if ( ok.equals("") 
                                && i.sourceBaseName.equals(a.baseName) 
                                && ! i.sourceClassName.equals(a.className) ) {
                            /* Source and dest are different and they are both have the 
                             * same parent, so they obviously one isn't a child of the other
                             */
                            ok = "ok";
                        }
                        
                        String line = String.format("%s\t%s\t%s\t%s\t%s\t%s\n", ok,
                                i.sourceClassName,
                                i.sourceMethodName,
                                a.className,
                                a.name,
                                a.getTarget());
                        
                        os.write(line.getBytes("UTF-8"));
                    }
                }
            }
            
            System.out.println("Done CSV");
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(os);
        }
        
    }
    
    private static class Invocation {
        String sourceClassName;
        String sourceBaseName;
        String sourceMethodName;
        String methodName;
        String methodDesc;

        public Invocation(String sourceClassName, String sourceBaseName, String sourceMethodName, String methodName, String methodDesc) {
            super();
            this.sourceClassName = sourceClassName;
            this.sourceBaseName = sourceBaseName;
            this.sourceMethodName = sourceMethodName;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }

        public String getTarget() {
            return methodName + "::" + methodDesc;
        }
        
        @Override
        public String toString() {
            return "Invocation [sourceClassName=" + sourceClassName + ", sourceBaseName=" + sourceBaseName
                    + ", sourceMethodName=" + sourceMethodName + ", methodName=" + methodName + ", methodDesc="
                    + methodDesc + "]";
        }
    }
    
    private static class Annotated {
        String className;
        String baseName;
        String name;
        String desc;
        boolean isPublic = false;

        public Annotated(String className, String baseName, String name, String desc, boolean isPublic) {
            super();
            this.className = className;
            this.baseName = baseName;
            this.name = name;
            this.desc = desc;
            this.isPublic = isPublic;
        }

        public String getTarget() {
            return name + "::" + desc;
        }
        
        @Override
        public String toString() {
            return "Annotated [className=" + className + ", baseName=" + baseName + ", name=" + name + ", desc=" + desc
                    + ", isPublic=" + isPublic + "]";
        }
        
    }
    
    private static class FindAnnotationWalker extends DirectoryWalker<Object> {

        FindCallers cv;
        int scanned = 0;
        
        @Override
        protected void handleFile(File file, int depth, Collection<Object> results) throws IOException {
            super.handleFile(file, depth, results);
            
            if ( file.getName().endsWith(".class") ) {
                InputStream is = null;
                try {
                    is = new FileInputStream(file);
                    ClassReader reader = new ClassReader(is);
                    
                    System.out.println("Scanning: " + file.getAbsolutePath());
                    reader.accept(cv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    scanned++;
                } finally {
                    IOUtils.closeQuietly(is);
                }
                
            }
        }
        
        public FindCallers walk(String path, String annotation) throws IOException {
            cv = new FindCallers(annotation);
            
            super.walk(new File(path), new ArrayList<Object>());
            
            System.out.println("Scanned " + scanned + " classes");
            return cv;
        }

    }
}
