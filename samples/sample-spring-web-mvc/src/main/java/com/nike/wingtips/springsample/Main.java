package com.nike.wingtips.springsample;

import com.nike.wingtips.servlet.RequestTracingFilter;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
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

import static com.nike.wingtips.servlet.RequestTracingFilter.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME;

/**
 * Starts up the Wingtips Spring Web MVC Sample server (on port 8080 by default).
 *
 * @author Nic Munroe
 */
public class Main {

    public static final String PORT_SYSTEM_PROP_KEY = "springSample.server.port";
    public static final String USER_ID_HEADER_KEYS = "userid,altuserid";
    private static int actualServerPort;

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
        actualServerPort = port;
        
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
        FilterHolder requestTracingFilterHolder = contextHandler.addFilter(
            RequestTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class)
        );
        requestTracingFilterHolder.setInitParameter(USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME, USER_ID_HEADER_KEYS);
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

        @Bean
        @Qualifier("serverPort")
        public int serverPort() {
            return actualServerPort;
        }

    }
}
