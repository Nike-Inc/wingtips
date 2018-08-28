package com.nike.wingtips.jersey1sample;

import com.nike.wingtips.jersey1sample.resource.SampleResource;
import com.nike.wingtips.servlet.RequestTracingFilter;

import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.core.servlet.WebAppResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

/**
 * Starts up the Wingtips Jersey 1 Sample server (on port 8080 by default).
 *
 * @author Nic Munroe
 */
public class Main {

    @SuppressWarnings("WeakerAccess")
    public static final String PORT_SYSTEM_PROP_KEY = "jersey1Sample.server.port";

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

        ResourceConfig rc = new WebAppResourceConfig(Collections.<String, Object>emptyMap(), contextHandler.getServletContext());
        rc.getSingletons().add(new SampleResource());
        ServletContainer jerseyServletContainer = new ServletContainer(rc);
        contextHandler.addServlet(SampleResource.SampleAsyncServlet.class, SampleResource.ASYNC_PATH);
        contextHandler.addServlet(SampleResource.SampleBlockingForwardServlet.class, SampleResource.BLOCKING_FORWARD_PATH);
        contextHandler.addServlet(SampleResource.SampleAsyncForwardServlet.class, SampleResource.ASYNC_FORWARD_PATH);
        contextHandler.addServlet(SampleResource.SampleAsyncTimeoutServlet.class, SampleResource.ASYNC_TIMEOUT_PATH);
        contextHandler.addServlet(SampleResource.SampleAsyncErrorServlet.class, SampleResource.ASYNC_ERROR_PATH);
        contextHandler.addServlet(new ServletHolder(jerseyServletContainer), "/*");
        contextHandler.addFilter(RequestTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        return contextHandler;
    }

}
