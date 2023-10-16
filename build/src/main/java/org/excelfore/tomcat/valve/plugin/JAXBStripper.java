package org.excelfore.tomcat.valve.plugin;

import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

@Mojo(name = "strip-jaxb", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class JAXBStripper extends AbstractMojo {


    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            doIt();

        } catch (Throwable e) {
            //noinspection ConstantValue
            if (e instanceof MojoFailureException) {
                throw (MojoFailureException) e;
            }
            throw new MojoExecutionException(e);
        }

    }


    @SneakyThrows
    protected void doIt() {

        Path dir = Paths.get(project.getBuild().getOutputDirectory());
        Map<Path, ClassReader> allClasses = new LinkedHashMap<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".class")) {
                    try (InputStream is = Files.newInputStream(file)) {
                        getLog().info("Loading "+file);
                        allClasses.put(file, new ClassReader(is));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (val me : allClasses.entrySet()) {

            ClassWriter cw = new ClassWriter(0);
            MyClassVisitor cv = new MyClassVisitor(cw);
            me.getValue().accept(cv, 0);
            if (cv.modified) {
                try (OutputStream os = Files.newOutputStream(me.getKey())) {
                    os.write(cw.toByteArray());
                }
            }

        }

    }

    protected String getAnnotationClass(String descriptor) {

        SignatureReader r = new SignatureReader(descriptor);
        Mutable<String> t = new Mutable<>();

        r.accept(new SignatureVisitor(Opcodes.ASM9) {
            @Override
            public void visitClassType(String name) {
                t.o = name;
            }
        });

        return t.o;

    }

    protected boolean isJaxbAnnotation(String descriptor) {

        String cls = getAnnotationClass(descriptor);
        return cls != null && cls.startsWith("jakarta/xml/bind/annotation");

    }

    class MyClassVisitor extends ClassVisitor {

        protected boolean modified = false;

        protected MyClassVisitor(ClassVisitor d) {
            super(Opcodes.ASM9, d);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            // return new MyAnnotationVisitor(super.visitAnnotation(descriptor, visible), "class "+descriptor);
            if (!isJaxbAnnotation(descriptor)) {
                return super.visitAnnotation(descriptor, visible);
            }
            getLog().info("Skipping annotation "+descriptor+" on top level");
            modified = true;
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    // return new MyAnnotationVisitor(super.visitAnnotation(descriptor, visible), "field "+name);
                    if (!isJaxbAnnotation(descriptor)) {
                        return super.visitAnnotation(descriptor, visible);
                    }
                    getLog().info("Skipping Annotation "+descriptor+" on field "+name);
                    modified = true;
                    return null;
                }
            };
        }

        // not doing methods, because there are no (currently known) XML annotations on methods


        class MyAnnotationVisitor extends AnnotationVisitor {

            final String on;

            protected MyAnnotationVisitor(AnnotationVisitor delegate, String on) {
                super(Opcodes.ASM9, delegate);
                this.on = on;
            }

            @Override
            public void visit(String name, Object value) {
                getLog().info("Annotation "+name+" on "+on);
                super.visit(name, value);
            }

        }

    }

    static class Mutable<T> {
        T o;
    }

}
