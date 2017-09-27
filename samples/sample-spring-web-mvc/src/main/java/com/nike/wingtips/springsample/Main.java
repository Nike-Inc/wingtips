package com.nike.wingtips.springsample;

import com.nike.wingtips.servlet.RequestTracingFilter;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

/**
 * Starts up the Wingtips Spring Web MVC Sample server (on port 8080 by default).
 *
 * @author Nic Munroe
 */
public class Main {

    public static final String PORT_SYSTEM_PROP_KEY = "springSample.server.port";

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
        server.setHandler(generateServletContextHandler(generateWebAppContext()));

        return server;
    }

    private static ServletContextHandler generateServletContextHandler(
        WebApplicationContext webappContext
    ) throws IOException {
        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");
        contextHandler.addServlet(new ServletHolder(new DispatcherServlet(webappContext)), "/*");
        contextHandler.addEventListener(new ContextLoaderListener(webappContext));
        contextHandler.addFilter(RequestTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        return contextHandler;
    }

    private static WebApplicationContext generateWebAppContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(SampleWebMvcConfig.class);
        return context;
    }

    @Configuration
    @ComponentScan(basePackages = "com.nike.wingtips.springsample")
    @EnableWebMvc
    private static class SampleWebMvcConfig extends WebMvcConfigurerAdapter {

        SampleWebMvcConfig() {}

    }
}
