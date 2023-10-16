package org.excelfore.tomcat.valve.plugin;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchema;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.lang.model.element.Modifier;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Mojo(name = "generate-jaxb", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class JAXBGenerator extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    protected List<TypeDeclaration<?>> types = new ArrayList<>();
    protected Map<String, TypeDeclaration<?>> loadableTypes = new HashMap<>();
    protected List<TypeDeclaration<?>> roots = new ArrayList<>();
    protected Map<String, AnnotationExpr> schemas = new HashMap<>();

    @Parameter(name = "generated-sources", defaultValue = "${project.build.directory}/generated-sources/jaxb")
    protected String generatedSources;

    protected ClassLoader buildClassLoader;

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
    protected URL newURL(String s) {

        File f = new File(s);
        if (f.isDirectory()) {
            if (!s.endsWith("/")) {
                s = s + "/";
            }
            return new URL("file:"+s);
        } else if (f.exists()) {
            return new URL("file:"+s);
        }

        return null;

    }

    @SneakyThrows
    protected void doIt() {

        // ProjectRoot projectRoot = new ProjectRoot(project.getBasedir().toPath());

        List<String> cpElements = project.getCompileClasspathElements();

        // IMPORTANT
        // we have to do this to prevent JavaParser loading classes that were earlier
        // compiled when doing symbol resolution. Otherwise, it may prefer loading a pre-compiled
        // class, and when resolving parent references of parsed classes, those may point
        // to the precompiled class. This messes up toAst() calls, because those from pre-compiled
        // code won't have AST. But we need AST due to annotation resolution - I didn't find any
        // way to get annotations from ResolvedFieldDeclaration. I'm probably missing something, but
        // I couldn't find what it was. As long as we rely on AST, this has to be done this way.
        // (Of course, the right way would be to ditch using AST all together)
        cpElements.removeIf(p->project.getBuild().getOutputDirectory().equals(p));

        buildClassLoader = new URLClassLoader(cpElements.stream().map(this::newURL).filter(Objects::nonNull).toArray(URL[]::new));

        TypeSolver typeSolver = new ClassLoaderTypeSolver(buildClassLoader);
        JavaSymbolSolver jsb = new JavaSymbolSolver(typeSolver);
        ParserConfiguration pc = new ParserConfiguration().setSymbolResolver(jsb);

        List<TypeSolver> self = new ArrayList<>();
        self.add(typeSolver);
        for (String s : project.getCompileSourceRoots()) {
            self.add(new JavaParserTypeSolver(Paths.get(s), pc));
        }

        // $TODO: this looks bonkers. Why can't we otherwise
        // resolve classes from other source roots?
        jsb = new JavaSymbolSolver(new CombinedTypeSolver(self));
        pc = new ParserConfiguration().setSymbolResolver(jsb);

        List<CompilationUnit> allUnits = new ArrayList<>();
        for (String s : project.getCompileSourceRoots()) {
            SourceRoot sr = new SourceRoot(Paths.get(s));
            sr.setParserConfiguration(pc);
            sr.tryToParseParallelized();
            allUnits.addAll(sr.getCompilationUnits());
        }

        for (CompilationUnit cu : allUnits) {
            cu.getPackageDeclaration().ifPresent(pd-> pd.getAnnotationByClass(XmlSchema.class).ifPresent(sa-> schemas.put(pd.getNameAsString(), sa)));
            for (val node : cu.getTypes()) {
                types.add(node);
                node.getAnnotationByClass(XmlRootElement.class).ifPresent(td->roots.add(node));
            }
        }

        getLog().info(types.size() + " types");
        getLog().info(roots.size() + " roots");
        getLog().info(schemas.size() + " schemas");

        roots.forEach(this::generateRoot);

        project.addCompileSourceRoot(generatedSources);

    }

    @SneakyThrows
    protected void generateRoot(TypeDeclaration<?> td) {

        getLog().info("Generating root loader for "+td.getFullyQualifiedName().orElse(td.getNameAsString()));

        String pkg = td.findCompilationUnit().orElseThrow().getPackageDeclaration().orElseThrow().getNameAsString();

        AnnotationExpr rootDef = td.getAnnotationByClass(XmlRootElement.class).orElseThrow();

        // $TODO: namespace can be defined by the tag itself.
        // String nameSpace = getAnnotationMember(schemaDef, "namespace").orElseThrow(()->new MojoFailureException("No namespace defined for "+pkg));
        String rootTag = getAnnotationMember(rootDef, "name").orElseThrow();

        ClassName loaderCn = ClassName.bestGuess(pkg + "." + td.getNameAsString() + "Loader");
        ClassName root = ClassName.bestGuess(td.getFullyQualifiedName().orElseThrow());
        TypeSpec.Builder tsb = TypeSpec.classBuilder(loaderCn).addModifiers(Modifier.PUBLIC);
        tsb.addField(FieldSpec.builder(InputSource.class, "input", Modifier.FINAL, Modifier.PROTECTED).build());
        tsb.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(InputStream.class, "src")
                        .addStatement("this.input = new $T(src)", InputSource.class)
                        .build()
        );

        String nameSpace = xmlSchemaOf(td);

        tsb.addMethod(
                MethodSpec.methodBuilder("unmarshal")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(root)
                        .addException(ParserConfigurationException.class)
                        .addException(IOException.class)
                        .addException(SAXException.class)
                        .addStatement("$1T dbf = $1T.newInstance()", DocumentBuilderFactory.class)
                        .addStatement("dbf.setFeature($T.FEATURE_SECURE_PROCESSING, true)", XMLConstants.class)
                        .addStatement("dbf.setNamespaceAware(true)")
                        .addStatement("$T builder = dbf.newDocumentBuilder()", DocumentBuilder.class)
                        .addStatement("$T document = builder.parse(this.input)", Document.class)
                        .addStatement("$T root = document.getDocumentElement()", Element.class)
                        .beginControlFlow("if (!$1T.equals(root.getNamespaceURI(), $2S) || !$1T.equals(root.getLocalName(), $3S))", Objects.class, nameSpace, rootTag)
                        .addStatement("throw new $T($T.format($S, $L))", RuntimeException.class, String.class, "Document root is %s:%s, but expected to be %s:%s",
                                CodeBlock.of("root.getNamespaceURI(), root.getLocalName(), $S, $S", nameSpace, rootTag))
                        .endControlFlow()
                        .addStatement("return unmarshal(root)")
                        .build());

        generateUnmarshal(tsb, td);

        writeSource(JavaFile.builder(pkg, tsb.build()).build());

    }

    protected void generateUnmarshal(TypeSpec.Builder tsb, TypeDeclaration<?> src) {

        ResolvedTypeDeclaration rtd = src.resolve();

        ClassName objClass = ClassName.bestGuess(src.getFullyQualifiedName().orElseThrow());

        MethodSpec.Builder method =
                MethodSpec.methodBuilder("unmarshal")
                        .addModifiers(Modifier.STATIC)
                        .returns(objClass)
                        .addParameter(Element.class, "element");

        method.addStatement("$1T obj = new $1T()", objClass);

        for (val fieldDecl : rtd.asReferenceType().getAllFields()) {

            FieldDeclaration fd = ((FieldDeclaration)fieldDecl.toAst().orElseThrow(()->new RuntimeException("Field "+fieldDecl.getName()+" from "+src+" is not from sources?")));
            if (fd.getAnnotationByClass(XmlTransient.class).isPresent()) { continue; }

            Optional<AnnotationExpr> asAttr = fd.getAnnotationByClass(XmlAttribute.class);
            if (asAttr.isPresent()) {
                loadFromAttribute(method, asAttr.get(), fieldDecl.asField());
            } else {
                loadFromElement(method, fieldDecl.asField());
            }

        }

        method.addStatement("return obj");
        tsb.addMethod(method.build());


    }

    protected void loadFromAttribute(MethodSpec.Builder method, AnnotationExpr attr, ResolvedFieldDeclaration fd) {

        String nameSpace = xmlSchemaOf((TypeDeclaration<?>) fd.declaringType().toAst().orElseThrow());

        // $TODO: the attribute name and NS can be specified in all kinds of ways
        method
                .beginControlFlow("")
                .addStatement("$T a = element.getAttributeNodeNS($S, $S)", Attr.class, nameSpace, fd.getName())
                // $TODO: we don't use NS qualified attributes, and I don't know what the rules are.
                // I'm gonna try loading from either
                .beginControlFlow("if (a == null)")
                .addStatement("a = element.getAttributeNodeNS(null, $S)", fd.getName())
                .endControlFlow()
                .beginControlFlow("if (a != null)")
                // .addStatement("obj.$L = $L", getFieldName(fd), stringToField(fd, CodeBlock.of("a.getValue()")))
                .addStatement("$T<$T, $T> set = $L", Function.class, String.class, getFieldType(fd).box(), stringToField(fd))
                .addStatement("obj.$L = set.apply(a.getValue())", fd.getName())
                .endControlFlow()
                .endControlFlow();

    }

    protected List<TypeDeclaration<?>> findXmlCandidates(ResolvedReferenceType parent) {

        List<TypeDeclaration<?>> result = new ArrayList<>();

        for (val candidate : types) {

            val resolved = candidate.resolve();
            if (resolved.getAllAncestors().contains(parent)) {
                result.add(findXmlType(candidate.getFullyQualifiedName().orElseThrow()).orElseThrow());
            }

        }

        return result;

    }

    protected Optional<TypeDeclaration<?>> findXmlType(ResolvedReferenceType rt) {
        return findXmlType(rt.getId());
    }

    protected Optional<TypeDeclaration<?>> findXmlType(String className) {

        val td = loadableTypes.get(className);
        if (td != null) { return Optional.of(td); }
        for (val candidate : types) {
            if (candidate.hasModifier(com.github.javaparser.ast.Modifier.Keyword.ABSTRACT)) { continue; }
            if (Objects.equals(candidate.getFullyQualifiedName().orElseThrow(), className)) {
                loadableTypes.put(className, candidate);
                generateType(candidate);
                return Optional.of(candidate);
            }
        }

        return Optional.empty();

    }

    protected void generateType(TypeDeclaration<?> td) {

        getLog().info("Generating type loader for "+td.getFullyQualifiedName().orElse(td.getNameAsString()));

        String pkg = td.findCompilationUnit().orElseThrow().getPackageDeclaration().orElseThrow().getNameAsString();
        ClassName loaderCn = ClassName.bestGuess(pkg + "." + td.getNameAsString() + "Loader");
        TypeSpec.Builder tsb = TypeSpec.classBuilder(loaderCn);
        generateUnmarshal(tsb, td);
        writeSource(JavaFile.builder(pkg, tsb.build()).build());

    }

    @SneakyThrows
    protected void loadFromElement(MethodSpec.Builder method, ResolvedFieldDeclaration fd) {

        // two possibilities here, either we are loading an array, or a complex type
        // $TODO: I'm sure there are a LOT more possibilities.

        String nameSpace = xmlSchemaOf((TypeDeclaration<?>) fd.declaringType().toAst().orElseThrow());

        String eName = xmlElementNameOf(fd);

        method
                .beginControlFlow("")
                .addStatement("$T match = element.getElementsByTagNameNS($S, $S)", NodeList.class, nameSpace, eName);

        ResolvedReferenceType rrt = fd.getType().asReferenceType();

        method.beginControlFlow("if (match.getLength() > 0)");

        ResolvedReferenceType use = null;
        boolean isList = false;

        // let's see if it's a collection
        for (val in : rrt.getAllInterfacesAncestors()) {
            if (Collection.class.getName().equals(in.getId())) {
                use = in.getTypeParametersMap().get(0).b.asReferenceType();
                isList = true;
                method
                        .beginControlFlow("for (int i = 0; i<match.getLength(); i++)")
                        .addStatement("$1T matchItem = ($1T)match.item(i)", Element.class);
                break;
            }
        }

        if (use == null) {
            method.addStatement("$1T matchItem = ($1T)match.item(0)", Element.class);
            use = rrt;
        }

        loadObject(fd, method, use, isList);

        if (isList) {
            method.endControlFlow();
        }

        method.endControlFlow().endControlFlow();

    }

    protected String xmlTypeOf(TypeDeclaration<?> td) {

        AnnotationExpr xmlType = td.getAnnotationByClass(XmlType.class).orElse(null);
        String r = null;
        if (xmlType != null) {
            r = getAnnotationMember(xmlType, "name").orElse(null);
        }
        if (r == null) {
            r = td.getNameAsString();
        }

        return r;

    }

    @SneakyThrows
    protected String xmlSchemaOf(TypeDeclaration<?> td) {

        // $TODO: this is really horribly wrong.
        // the package determination is not well done
        // the namespace can actually be defined in the type/root element of the class
        // and so on.

        String pkg = td.findCompilationUnit().orElseThrow().getPackageDeclaration().orElseThrow().getNameAsString();
        AnnotationExpr schemaDef = schemas.get(pkg);
        if (schemaDef == null) {
            throw new MojoFailureException("No schema found for xml root "+td.getNameAsString()+" (package "+pkg+"), missing package-info.java?");
        }
        return getAnnotationMember(schemaDef, "namespace").orElseThrow();

    }

    @SneakyThrows
    protected String xmlElementNameOf(ResolvedFieldDeclaration tfd) {

        FieldDeclaration fd = (FieldDeclaration) tfd.toAst().orElseThrow();
        AnnotationExpr el = fd.getAnnotationByClass(XmlElement.class).orElse(null);
        String name = null;
        if (el != null) {
            name = getAnnotationMember(el, "name").orElse(null);
        }
        if (name == null) {
            name = tfd.getName();
        }

        return name;

    }

    @SneakyThrows
    protected void loadObject(ResolvedFieldDeclaration fd, MethodSpec.Builder method, ResolvedReferenceType rrt, boolean isList) {

        TypeDeclaration<?> direct = findXmlType(rrt).orElse(null);
        method.addStatement("$T value = null", ClassName.bestGuess(rrt.getId()));
        if (direct != null) {
            String fqdn = direct.getFullyQualifiedName().orElseThrow();
            method.addStatement("value = $T.unmarshal(matchItem)", ClassName.bestGuess(fqdn + "Loader"));
        } else {

            List<TypeDeclaration<?>> options = findXmlCandidates(rrt);
            if (options.isEmpty()) {
                throw new MojoFailureException("No xml types can be found for class "+rrt.getId());
            }

            method.addStatement("$T useType = matchItem.getAttributeNS($T.W3C_XML_SCHEMA_INSTANCE_NS_URI, $S)", String.class, XMLConstants.class, "type");

            for (val option : options) {

                // $TODO lookupPrefix() is not great when there are potential multiple prefixes
                // but otherwise there is no "simple" way to map prefix to a NS (so we are doing it
                // backwards, resolve NS to a prefix, which is ambiguous), but to do it "right", we
                // need a whole lot of work, see http://www.java2s.com/Code/Java/XML/Startingfromanodefindthenamespacedeclarationforaprefix.htm

                method.beginControlFlow("if ($T.equals(useType, matchItem.lookupPrefix($S) + $S))", Objects.class, xmlSchemaOf(option), ":" + xmlTypeOf(option));
                method.addStatement("value = $T.unmarshal(matchItem)", ClassName.bestGuess(option.getFullyQualifiedName().orElseThrow()+"Loader"));
                method.endControlFlow();
            }

        }

        method.beginControlFlow("if (value != null)");
        if (isList) {
            method.addStatement("obj.$L().add(value)", CodeGenerationUtils.getterName(Integer.class, fd.getName()));
        } else {
            method.addStatement("obj.$L = value", fd.getName());
        }
        method.endControlFlow();

    }

    // generates a lambda that will convert
    protected CodeBlock stringToField(FieldDeclaration fd) {

        Type varType = fd.getVariables().get(0).getType();
        String classRef;
        ResolvedType rVarType = varType.resolve();
        if (rVarType.isReferenceType()) {
            classRef = rVarType.asReferenceType().getId();
        } else {
            classRef = null;
        }
        if (Objects.equals(String.class.getName(), varType.asString())) {
            return CodeBlock.of("value->value");
        } else if ("boolean".equals(varType.asString()) || Boolean.class.getName().equals(classRef)) {
            return CodeBlock.of("value->$T.valueOf(value)", Boolean.class);
        } else if ("int".equals(varType.asString()) || Integer.class.getName().equals(classRef)) {
            return CodeBlock.of("value->$T.parseInt(value)", Integer.class);
        } else {
            throw new RuntimeException("Unsupported class for string conversion: "+varType);
        }

    }

    protected CodeBlock stringToField(ResolvedFieldDeclaration fd) {

        String pType = getFieldType(fd).toString();

        ResolvedType rVarType = fd.getType();
        String classRef;
        if (rVarType.isReferenceType()) {
            classRef = rVarType.asReferenceType().getId();
        } else {
            classRef = null;
        }
        if (Objects.equals(String.class.getName(), pType)) {
            return CodeBlock.of("value->value");
        } else if ("boolean".equals(pType) || Boolean.class.getName().equals(classRef)) {
            return CodeBlock.of("value->$T.valueOf(value)", Boolean.class);
        } else if ("int".equals(pType) || Integer.class.getName().equals(classRef)) {
            return CodeBlock.of("value->$T.parseInt(value)", Integer.class);
        } else {
            throw new RuntimeException("Unsupported class for string conversion: "+pType);
        }

    }

    @SneakyThrows
    protected String getFieldName(FieldDeclaration fd) {

        val x = fd.getVariables();
        if (x.size() != 1) {
            throw new MojoFailureException("Field declarations should be for a single field");
        }
        return x.get(0).getNameAsString();

    }

    @SneakyThrows
    protected TypeName getFieldType(FieldDeclaration fd) {

        val x = fd.getVariables();
        if (x.size() != 1) {
            throw new MojoFailureException("Field declarations should be for a single field");
        }
        val f = x.get(0);
        Type t = f.getType();
        if (t.isPrimitiveType()) {
            return (TypeName)TypeName.class.getDeclaredField(t.asString().toUpperCase()).get(null);
        }
        return ClassName.bestGuess(t.asString());

    }

    @SneakyThrows
    protected TypeName getFieldType(ResolvedFieldDeclaration fd) {

        ResolvedType rt = fd.getType();
        if (rt.isPrimitive()) {
            return (TypeName)TypeName.class.getDeclaredField(rt.asPrimitive().name()).get(null);
        }
        return ClassName.bestGuess(rt.asReferenceType().getId());

    }

    @SneakyThrows
    protected void writeSource(JavaFile jf) {

        jf.writeTo(new File(generatedSources));

    }

    protected Optional<String> getAnnotationMember(AnnotationExpr annotation, String property) {

        for (val node : annotation.getChildNodes()) {

            if (!(node instanceof MemberValuePair)) { continue; }

            val mvp = (MemberValuePair) node;
            if (!Objects.equals(mvp.getName().getIdentifier(), property)) { continue; }

            // $TODO: those can be references.
            return Optional.of(mvp.getValue().asLiteralStringValueExpr().getValue());

        }

        return Optional.empty();

    }


}
