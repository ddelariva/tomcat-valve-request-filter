package org.excelfore.tomcat.valve.acl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.excelfore.tomcat.valve.acl.cfg.Behavior;
import org.excelfore.tomcat.valve.acl.cfg.Influence;
import org.excelfore.tomcat.valve.acl.cfg.Match;

import jakarta.servlet.ServletException;

/**
 * @author Diego De la Riva
 */
public class TomcatValve extends ValveBase {

    private static final Log log = LogFactory.getLog(TomcatValve.class);

    private Behavior behavior;

    public TomcatValve() {
    }

    public void setConfig(String config) {

        behavior = loadConfig(config);

    }

    private Behavior loadConfig(String config) {

        /*

        I considered using Apache Digester.
        It's simple, and will work, but then I'll have to
        maintain a separate schema documentation, and ensure
        that that schema is actually valid, and XML schemas
        are just too tiresome unless enforced programmatically.

        It may be painful if we end up having to deal with JAXBContext
        problems when we migrate to/add support for JDK 17.

        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setRulesValidation(true);

        // I'm grudgingly using Digester, as it's pretty
        // simple. I considered using @XmlSchema, but
        // I don't want to then invoke

        Map<Class<?>, List<String>> fakeAttributes = new HashMap<>();

        List<String> objectAttrs = new ArrayList<>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);

        digester.addObjectCreate("ACL",
                Behavior.class.getName(),
                null);
        digester.addSetProperties("ACL");
        digester.addObjectCreate("ACL/Match",
                Match.class.getName(),
                null);
        digester.addSetProperties("ACL/Match");
        digester.addSetNext("Match",
                "addMatch",
                Match.class.getName());
        */

        try {
            Path p = Paths.get(config).toAbsolutePath();
            config = p.toString();
            Behavior b = Configurator.loadConfiguration(p);
            b.validate();
            return b;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse configuration from "+config, e);
        }

    }

    public void invoke(Request request, Response response) throws IOException, ServletException {

        if (behavior == null) {
            throw new IllegalArgumentException("Valve has not been configured");
        }

        if (!behavior.match(request)) {
            next(request, response);
            return;
        }

        for (Match m : behavior.getMatches()) {
            if (m.matches(request)) {
                influence(m, request, response);
                return;
            }
        }

        influence(behavior, request, response);

    }

    public void next(Request request, Response response) throws IOException, ServletException {
        Valve next = getNext();
        if (next != null) { next.invoke(request, response); }
    }

    public Log getLog() {
        return log;
    }

    private void influence(Influence influence, Request request, Response response) throws ServletException, IOException {

        influence.apply(this, request, response);

    }

    public Behavior getConfig() {
        return behavior;
    }

}
