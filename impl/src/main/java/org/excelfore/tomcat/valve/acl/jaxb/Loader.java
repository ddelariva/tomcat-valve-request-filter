package org.excelfore.tomcat.valve.acl.jaxb;

import org.excelfore.tomcat.valve.acl.cfg.Behavior;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Poor-man JAXB implementation, since JDK doesn't ship it anymore,
 * Tomcat doesn't have it, and I don't want JAXB as a dependency
 * because it may mess up the applications.
 */
public class Loader {

    final List<Class<?>> allClasses;


    public Loader(Package p) {

        // package tells us what the schema namespace is.
        // all classes in this package are for that schema namespace.
        // JAXB looks for ObjectFactory class, or for jaxb.index.
        // We use have jaxb.index.

        try {
            List<Class<?>> classes = loadFromJaxbIndex(p.getName(), getClass().getClassLoader());
            allClasses = new ArrayList<>(classes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JAXB context", e);
        }


    }

    private List<Class<?>> loadFromJaxbIndex(String pkg, ClassLoader cl) throws Exception {

        final String resource = pkg.replace('.', '/') + "/jaxb.index";
        final InputStream resourceAsStream = cl.getResourceAsStream(resource);

        if (resourceAsStream == null) {
            return null;
        }

        List<Class<?>> list = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(resourceAsStream, "UTF-8"))) {
            String className = in.readLine();
            while (className != null) {
                className = className.trim();
                if (className.startsWith("#") || (className.isEmpty())) {
                    className = in.readLine();
                    continue;
                }

                list.add(cl.loadClass(pkg + '.' + className));

            }
        }
        return null;


    }

    private InputSource is;

    public void setInput(InputStream is) {
        this.is = new InputSource(is);
    }

    public void load() throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setNamespaceAware(true);
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.parse(is);

        Element e = document.getDocumentElement();
        //e.getOwnerDocument()


    }

}
