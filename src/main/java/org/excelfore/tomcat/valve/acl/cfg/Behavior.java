package org.excelfore.tomcat.valve.acl.cfg;

import org.apache.catalina.connector.Request;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures valve behavior. The influence parameter specify the behavior
 * of the valve that is applied if none of the matches specified within
 * matched the incoming request.
 */
@XmlRootElement(name = "tomcat-valve-request")
public class Behavior extends Influence {

    /**
     * If {@code true}, then valve will produce messages, when matched, with {@code debug} level.
     * Otherwise, they are produced with {@code info} level.
     */
    @XmlAttribute
    protected boolean asDebug;

    @XmlAttribute
    protected Integer connector;

    /**
     * List of matching rules.
     */
    @XmlElement(name = "match")
    protected List<Match> matches = new ArrayList<>();

    public List<Match> getMatches() {
        return matches;
    }

    public void validate() {
        super.validate();
        matches.forEach(Match::validate);
    }

    @Override
    public String getMatchExplain() {
        return "DEFAULT";
    }

    public boolean isAsDebug() {
        return asDebug;
    }

    public boolean match(Request request) {

        if (connector == null) { return true; }

        return request.getConnector().getPort() == connector;
    }
}
