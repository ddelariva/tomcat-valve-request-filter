package org.excelfore.tomcat.valve.acl;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class TomcatValveTest {

    static class TerminatingValve extends ValveBase {
        @Override
        public void invoke(Request request, Response response) {
        }
    }

    public static class MockResponse extends Response {
        private int status = HttpServletResponse.SC_OK;

        @Override
        public void sendError(int status) {
            this.status = status;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }

    private void oneTest(String configResource, int port, String path, int expectedResult) throws Exception {
        // PREPARE
        Connector connector = new Connector();
        connector.setPort(port);
        Context context = new StandardContext();
        Request request = new Request(connector) {
            @Override
            public String getRequestURI() {
                return path;
            }
        };
        Response response = new MockResponse();

        request.getMappingData().context = context;
        request.setCoyoteRequest(new org.apache.coyote.Request());
        TomcatValve valve = new TomcatValve();

        File temp = File.createTempFile(getClass().getSimpleName(), "xml");
        try (InputStream is = getClass().getResourceAsStream(configResource); OutputStream os = new FileOutputStream(temp)) {
            Assertions.assertNotNull(is, ()->"Failed to load config resource "+configResource);
            byte [] buf = new byte[16384];
            while (true) {
                int nr = is.read(buf);
                if (nr < 0) { break; }
                os.write(buf, 0, nr);
            }
        }

        valve.setNext(new TerminatingValve());
        valve.setConfig(temp.getAbsolutePath());
        valve.invoke(request, response);

        Assertions.assertEquals(expectedResult, response.getStatus());

    }

    @Test
    public void test1() throws Exception {

        String conf = "/conf1.xml";

        oneTest(conf, 0, "/good", HttpServletResponse.SC_OK);
        oneTest(conf, 0,"/bad", HttpServletResponse.SC_NOT_FOUND);
        oneTest(conf, 0, "/other", HttpServletResponse.SC_BAD_REQUEST);

    }

    @Test
    public void test2() throws Exception {

        String conf = "/conf2.xml";

        oneTest(conf, 100, "/", HttpServletResponse.SC_OK);
        oneTest(conf, 200, "/", HttpServletResponse.SC_BAD_REQUEST);

    }

}
