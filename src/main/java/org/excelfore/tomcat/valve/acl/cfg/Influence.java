package org.excelfore.tomcat.valve.acl.cfg;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.excelfore.tomcat.valve.acl.TomcatValve;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Represents an influence on the request. The influence is determined
 * based on {@code outcome} property.
 */
public abstract class Influence {

    /**
     * Outcome of the influence.
     */
    protected Outcome outcome;

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public void validate() {
        if (outcome == null) { throw new IllegalArgumentException("No outcome specified"); }
        outcome.validate();
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void apply(TomcatValve valve, Request request, Response response) throws ServletException, IOException {
        Behavior config = valve.getConfig();
        Log log = valve.getLog();
        Consumer<String> doLog = config.asDebug ? log::debug : log::info;
        doLog.accept("Match "+getMatchExplain() + ": "+outcome.getExplain()+" against URI "+request.getRequestURI());
        outcome.apply(valve, request, response);
    }

    public abstract String getMatchExplain();

}
