package org.excelfore.tomcat.valve.acl.cfg;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.excelfore.tomcat.valve.acl.TomcatValve;

import javax.servlet.ServletException;
import javax.xml.bind.annotation.XmlType;
import java.io.IOException;

/**
 * Indicates that the outcome should be to continue processing the request
 * by further valves.
 */
@XmlType(name = "Continue")
public class Continue extends Outcome {

    @Override
    public void apply(TomcatValve valve, Request request, Response response) throws ServletException, IOException {
        valve.next(request, response);
    }

    @Override
    public String getExplain() {
        return "PASS-THROUGH";
    }

}
