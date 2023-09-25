package org.excelfore.tomcat.valve.acl;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.excelfore.tomcat.valve.acl.cfg.Behavior;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public class Configurator {

    public static JAXBContext jaxbContext() throws Exception {
        return JAXBContext.newInstance(Behavior.class.getPackage().getName());
    }


    public static <T> T unmarshal(InputSource input, JAXBContext jaxb) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document d = db.parse(input);
        return unmarshal(d, jaxb);
    }

    public static <T> T unmarshal(Document d, JAXBContext jaxb) throws Exception {
        Unmarshaller ums = jaxb.createUnmarshaller();
        //noinspection unchecked
        return (T) ums.unmarshal(d);
    }

    public static Behavior loadConfiguration(Path filePath) throws Exception {
        return unmarshal(new InputSource(Files.newInputStream(filePath)), jaxbContext());
    }

}
