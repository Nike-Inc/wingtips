package com.nike.wingtips.jersey2.componenttest;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.jersey2.SpanCustomizingApplicationEventListener;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.servlet.RequestTracingFilter;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.servlet.DispatcherType;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import io.restassured.response.ExtractableResponse;

import static com.nike.wingtips.jersey2.componenttest.WingtipsJersey2ComponentTest.Jersey2Resource.PATH_PARAM_ENDPOINT_PATH;
import static com.nike.wingtips.jersey2.componenttest.WingtipsJersey2ComponentTest.Jersey2Resource.PATH_PARAM_ENDPOINT_PATH_PREFIX;
import static com.nike.wingtips.jersey2.componenttest.WingtipsJersey2ComponentTest.Jersey2Resource.PATH_PARAM_ENDPOINT_PAYLOAD;
import static com.nike.wingtips.jersey2.componenttest.WingtipsJersey2ComponentTest.Jersey2Resource.WILDCARD_ENDPOINT_ENDPOINT_PAYLOAD;
import static com.nike.wingtips.jersey2.componenttest.WingtipsJersey2ComponentTest.Jersey2Resource.WILDCARD_ENDPOINT_PATH;
import static com.nike.wingtips.jersey2.componenttest.WingtipsJersey2ComponentTest.Jersey2Resource.WILDCARD_ENDPOINT_PATH_PREFIX;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A component test verifying the Wingtips+Jersey2 functionality in a real running server.
 *
 * @author Nic Munroe
 */
public class WingtipsJersey2ComponentTest {

    private static final int SERVER_PORT = findFreePort();
    private static Server server;

    private SpanRecorder spanRecorder;

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = createServer(SERVER_PORT);
        server.start();
        for (int i = 0; i < 100; i++) {
            if (server.isStarted())
                return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Server is not up after waiting 10 seconds. Aborting tests.");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
            server.destroy();
        }
    }

    @Before
    public void beforeMethod() {
        clearTracerSpanLifecycleListeners();

        spanRecorder = new SpanRecorder();
        Tracer.getInstance().addSpanLifecycleListener(spanRecorder);
    }

    @After
    public void afterMethod() {
        clearTracerSpanLifecycleListeners();
    }

    private void clearTracerSpanLifecycleListeners() {
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
    }

    @Test
    public void path_param_calls_result_in_span_name_with_path_template() {
        String id = UUID.randomUUID().toString();

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .log().all()
            .when()
                .get(PATH_PARAM_ENDPOINT_PATH_PREFIX + "/" + id)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(PATH_PARAM_ENDPOINT_PAYLOAD);

        assertThat(spanRecorder.completedSpans).hasSize(1);
        assertThat(spanRecorder.completedSpans.get(0).getSpanName())
            .doesNotContain(id)
            .isEqualTo("GET " + PATH_PARAM_ENDPOINT_PATH);
    }

    @Test
    public void wildcard_path_calls_result_in_span_name_with_path_template() {
        String randomPathSegment = UUID.randomUUID().toString();

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(SERVER_PORT)
                .log().all()
            .when()
                .get(WILDCARD_ENDPOINT_PATH_PREFIX + "/" + randomPathSegment + "/foo")
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(WILDCARD_ENDPOINT_ENDPOINT_PAYLOAD);

        assertThat(spanRecorder.completedSpans).hasSize(1);
        assertThat(spanRecorder.completedSpans.get(0).getSpanName())
            .doesNotContain(randomPathSegment)
            .isEqualTo("GET " + WILDCARD_ENDPOINT_PATH);
    }

    @Provider
    @Path("")
    public static class Jersey2Resource {
        public static final String PATH_PARAM_ENDPOINT_PATH_PREFIX = "/some/resource";
        public static final String PATH_PARAM_ENDPOINT_PATH = PATH_PARAM_ENDPOINT_PATH_PREFIX + "/{id}";
        public static final String PATH_PARAM_ENDPOINT_PAYLOAD =
            "path-param-endpoint-" + UUID.randomUUID().toString();

        public static final String WILDCARD_ENDPOINT_PATH_PREFIX = "/wildcard";
        public static final String WILDCARD_ENDPOINT_PATH = WILDCARD_ENDPOINT_PATH_PREFIX + "/{restOfPath:.+}";
        public static final String WILDCARD_ENDPOINT_ENDPOINT_PAYLOAD =
            "wildcard-endpoint-" + UUID.randomUUID().toString();

        @GET
        @Path(PATH_PARAM_ENDPOINT_PATH)
        public String getPathParamEndpoint() {
            return PATH_PARAM_ENDPOINT_PAYLOAD;
        }

        @GET
        @Path(WILDCARD_ENDPOINT_PATH)
        public String getWildcardEndpoint() {
            return WILDCARD_ENDPOINT_ENDPOINT_PAYLOAD;
        }
    }

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
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

        ServletContainer jerseyServletContainer = new ServletContainer(new Jersey2ResourceConfig());
        contextHandler.addServlet(new ServletHolder(jerseyServletContainer), "/*");
        contextHandler.addFilter(RequestTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        return contextHandler;
    }

    private static class Jersey2ResourceConfig extends ResourceConfig {

        public Jersey2ResourceConfig() {
            register(new Jersey2Resource());
            register(SpanCustomizingApplicationEventListener.create());
        }

    }

    @SuppressWarnings("WeakerAccess")
    public static class SpanRecorder implements SpanLifecycleListener {

        public final List<Span> completedSpans = new ArrayList<>();

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            completedSpans.add(span);
        }
    }

}
