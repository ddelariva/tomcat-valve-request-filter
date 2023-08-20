package org.excelfore.tomcat.valve.acl.cfg;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.excelfore.tomcat.valve.acl.TomcatValve;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Abstract class service as bases for operations outcome.
 */
public abstract class Outcome {

    public abstract void apply(TomcatValve valve, Request request, Response response) throws ServletException, IOException;

    public void validate() {}


    public abstract String getExplain();

}
