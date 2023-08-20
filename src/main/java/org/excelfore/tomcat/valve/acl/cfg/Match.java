package org.excelfore.tomcat.valve.acl.cfg;

import org.apache.catalina.connector.Request;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import java.util.regex.Pattern;

/**
 * Specifies a pattern match associated with an outcome.
 * If the request URI matches the specified pattern, the outcome is applied.
 */
public class Match extends Influence {

    /**
     * Pattern to match the request against. See JDK 8 Pattern specification for syntax details.
     */
    @XmlAttribute
    protected String pattern;

    @XmlTransient
    protected Pattern aPattern;

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void validate() {
        super.validate();
        this.aPattern = Pattern.compile(pattern);
    }

    @Override
    public String getMatchExplain() {
        return "MATCH "+pattern;
    }

    public boolean matches(Request request) {

        String uri = request.getRequestURI();

        if (uri == null) { return false; }

        return aPattern.matcher(uri).matches();

    }
}
