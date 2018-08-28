package com.nike.wingtips.jersey2sample;

import com.nike.wingtips.jersey2.SpanCustomizingApplicationEventListener;
import com.nike.wingtips.jersey2sample.resource.SampleResource;
import com.nike.wingtips.servlet.RequestTracingFilter;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

/**
 * Starts up the Wingtips Jersey 2 Sample server (on port 8080 by default).
 *
 * @author Nic Munroe
 */
public class Main {

    @SuppressWarnings("WeakerAccess")
    public static final String PORT_SYSTEM_PROP_KEY = "jersey2Sample.server.port";

    public static void main(String[] args) throws Exception {
        Server server = createServer(Integer.parseInt(System.getProperty(PORT_SYSTEM_PROP_KEY, "8080")));

        try {
            server.start();
            server.join();
        }
        finally {
            server.destroy();
        }
    }

    public static Server createServer(int port) throws Exception {
        Server server = new Server(port);
        server.setHandler(generateServletContextHandler());

        return server;
    }

    private static ServletContextHandler generateServletContextHandler() throws IOException {
        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");

        ResourceConfig rc = new ResourceConfig();
        rc.register(new SampleResource());
        rc.register(SpanCustomizingApplicationEventListener.create());
        contextHandler.addServlet(new ServletHolder(new ServletContainer(rc)), "/*");
        contextHandler.addFilter(RequestTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        return contextHandler;
    }
}
