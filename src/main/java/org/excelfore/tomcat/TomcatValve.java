package org.excelfore.tomcat;

import java.io.IOException;
import java.util.Arrays;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Diego De la Riva
 */
public class TomcatValve extends ValveBase {
    private String urlPatterns;

    public void setUrlPatterns(String urlPatterns) {
        this.urlPatterns = urlPatterns;
    }

    public void invoke(Request request, Response response) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = request.getRequest();

        boolean matchesAnyPattern = Arrays.stream(urlPatterns.split(","))
                .anyMatch(pattern -> httpServletRequest.getRequestURI().matches(pattern));

        if (matchesAnyPattern) {
            getNext().invoke(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
