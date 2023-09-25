package org.excelfore.tomcat.valve.acl.cfg;


import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.excelfore.tomcat.valve.acl.TomcatValve;

import java.io.IOException;

/**
 * An outcome that
 */
@XmlType(name = "SendError")
public class SendError extends Outcome {

    @XmlAttribute
    Integer code;

    @Override
    public void apply(TomcatValve valve, Request request, Response response) throws IOException {
        response.sendError(code);
    }

    @Override
    public void validate() {
        if (code == null) {
            throw new IllegalArgumentException("code must be specified");
        }
    }

    @Override
    public String getExplain() {
        return "RETURN "+code;
    }
}
