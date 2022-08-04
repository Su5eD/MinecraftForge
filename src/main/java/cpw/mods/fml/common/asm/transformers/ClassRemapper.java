/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
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
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package cpw.mods.fml.common.asm.transformers;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.Remapper;

/**
 * ASM 5.2 ClassRemapper backport.
 * 
 * A {@link ClassVisitor} for type remapping.
 * 
 * @author Eugene Kuleshov
 */
public class ClassRemapper extends ClassVisitor {

    protected final Remapper remapper;

    protected String className;

    public ClassRemapper(final ClassVisitor cv, final Remapper remapper) {
        this(Opcodes.ASM4, cv, remapper);
    }

    protected ClassRemapper(final int api, final ClassVisitor cv, final Remapper remapper) {
        super(api, cv);
        this.remapper = remapper;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, remapper.mapType(name), remapper
                .mapSignature(signature, false), remapper.mapType(superName),
                interfaces == null ? null : remapper.mapTypes(interfaces));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(remapper.mapDesc(desc),
                visible);
        return av == null ? null : createAnnotationRemapper(av);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        FieldVisitor fv = super.visitField(access,
                remapper.mapFieldName(className, name, desc),
                remapper.mapDesc(desc), remapper.mapSignature(signature, true),
                remapper.mapValue(value));
        return fv == null ? null : createFieldRemapper(fv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        String newDesc = remapper.mapMethodDesc(desc);
        MethodVisitor mv = super.visitMethod(access, remapper.mapMethodName(
                className, name, desc), newDesc, remapper.mapSignature(
                signature, false),
                exceptions == null ? null : remapper.mapTypes(exceptions));
        return mv == null ? null : createMethodRemapper(mv);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(remapper.mapType(name), outerName == null ? null
                : remapper.mapType(outerName), innerName, access);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        super.visitOuterClass(remapper.mapType(owner), name == null ? null
                : remapper.mapMethodName(owner, name, desc),
                desc == null ? null : remapper.mapMethodDesc(desc));
    }

    protected FieldVisitor createFieldRemapper(FieldVisitor fv) {
        return new FieldRemapper(fv, remapper);
    }

    protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
        return new MethodRemapper(mv, remapper);
    }

    protected AnnotationVisitor createAnnotationRemapper(AnnotationVisitor av) {
        return new AnnotationRemapper(av, remapper);
    }
    
    /**
     * A {@link FieldVisitor} adapter for type remapping.
     * 
     * @author Eugene Kuleshov
     */
    public static class FieldRemapper extends FieldVisitor {

        private final Remapper remapper;

        public FieldRemapper(final FieldVisitor fv, final Remapper remapper) {
            this(Opcodes.ASM4, fv, remapper);
        }

        protected FieldRemapper(final int api, final FieldVisitor fv, final Remapper remapper) {
            super(api, fv);
            this.remapper = remapper;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            AnnotationVisitor av = fv.visitAnnotation(remapper.mapDesc(desc), visible);
            return av == null ? null : new AnnotationRemapper(av, remapper);
        }
    }

    /**
     * An {@link AnnotationVisitor} adapter for type remapping.
     *
     * @author Eugene Kuleshov
     */
    public static class AnnotationRemapper extends AnnotationVisitor {

        protected final Remapper remapper;

        public AnnotationRemapper(final AnnotationVisitor av, final Remapper remapper) {
            this(Opcodes.ASM4, av, remapper);
        }

        protected AnnotationRemapper(final int api, final AnnotationVisitor av, final Remapper remapper) {
            super(api, av);
            this.remapper = remapper;
        }

        @Override
        public void visit(String name, Object value) {
            av.visit(name, remapper.mapValue(value));
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            av.visitEnum(name, remapper.mapDesc(desc), value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            AnnotationVisitor v = av.visitAnnotation(name, remapper.mapDesc(desc));
            return v == null ? null : v == av ? this : new AnnotationRemapper(v, remapper);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor v = av.visitArray(name);
            return v == null ? null : v == av ? this : new AnnotationRemapper(v, remapper);
        }
    }

    public static class MethodRemapper extends MethodVisitor {

        protected final Remapper remapper;

        public MethodRemapper(final MethodVisitor mv, final Remapper remapper) {
            this(Opcodes.ASM4, mv, remapper);
        }

        protected MethodRemapper(final int api, final MethodVisitor mv, final Remapper remapper) {
            super(api, mv);
            this.remapper = remapper;
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            AnnotationVisitor av = super.visitAnnotationDefault();
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            AnnotationVisitor av = super.visitParameterAnnotation(parameter, remapper.mapDesc(desc), visible);
            return av == null ? av : new AnnotationRemapper(av, remapper);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            super.visitFrame(type, nLocal, remapEntries(nLocal, local), nStack,
                remapEntries(nStack, stack));
        }

        private Object[] remapEntries(int n, Object[] entries) {
            for (int i = 0; i < n; i++) {
                if (entries[i] instanceof String) {
                    Object[] newEntries = new Object[n];
                    if (i > 0) {
                        System.arraycopy(entries, 0, newEntries, 0, i);
                    }
                    do {
                        Object t = entries[i];
                        newEntries[i++] = t instanceof String ? remapper
                            .mapType((String) t) : t;
                    } while (i < n);
                    return newEntries;
                }
            }
            return entries;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            super.visitFieldInsn(opcode, remapper.mapType(owner),
                remapper.mapFieldName(owner, name, desc),
                remapper.mapDesc(desc));
        }

        @Deprecated
        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc) {
            if (api >= Opcodes.ASM4) {
                super.visitMethodInsn(opcode, owner, name, desc);
                return;
            }
            doVisitMethodInsn(opcode, owner, name, desc);
        }

        private void doVisitMethodInsn(int opcode, String owner, String name, String desc) {
            // Calling super.visitMethodInsn requires to call the correct version
            // depending on this.api (otherwise infinite loops can occur). To
            // simplify and to make it easier to automatically remove the backward
            // compatibility code, we inline the code of the overridden method here.
            // IMPORTANT: THIS ASSUMES THAT visitMethodInsn IS NOT OVERRIDDEN IN
            // LocalVariableSorter.
            if (mv != null) {
                mv.visitMethodInsn(opcode, remapper.mapType(owner),
                    remapper.mapMethodName(owner, name, desc),
                    remapper.mapMethodDesc(desc));
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            for (int i = 0; i < bsmArgs.length; i++) {
                bsmArgs[i] = remapper.mapValue(bsmArgs[i]);
            }
            super.visitInvokeDynamicInsn(
                remapper.mapInvokeDynamicMethodName(name, desc),
                remapper.mapMethodDesc(desc), (Handle) remapper.mapValue(bsm),
                bsmArgs);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, remapper.mapType(type));
        }

        @Override
        public void visitLdcInsn(Object cst) {
            super.visitLdcInsn(remapper.mapValue(cst));
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(remapper.mapDesc(desc), dims);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type == null ? null
                : remapper.mapType(type));
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            super.visitLocalVariable(name, remapper.mapDesc(desc),
                remapper.mapSignature(signature, true), start, end, index);
        }
    }
}

