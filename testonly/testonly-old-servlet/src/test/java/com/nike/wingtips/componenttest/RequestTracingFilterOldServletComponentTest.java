package com.nike.wingtips.componenttest;

import com.nike.internal.util.MapBuilder;
import com.nike.internal.util.Pair;
import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.servlet.RequestTracingFilter;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.restassured.response.ExtractableResponse;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Component test to verify that {@link RequestTracingFilter} works as expected when deployed to a real running server
 * that *only* supports Servlet 2.x (no Servlet 3 API available on the classpath).
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class RequestTracingFilterOldServletComponentTest {

    private static int port;
    private static Server server;

    private SpanRecorder spanRecorder;

    @BeforeClass
    @SuppressWarnings("JavaReflectionMemberAccess")
    public static void beforeClass() throws Exception {
        try {
            ServletRequest.class.getMethod("getAsyncContext");
            fail(
                "Expected this test to run in an environment that does *NOT* support Servlet 3 API, "
                + "however ServletRequest.getAsyncContext() method was found."
            );
        }
        catch(NoSuchMethodException ex) {
            // Expected - do nothing
        }

        try {
            Class.forName("javax.servlet.AsyncListener");
            fail(
                "Expected this test to run in an environment that does *NOT* support Servlet 3 API, "
                + "however javax.servlet.AsyncListener class was found."
            );
        }
        catch(ClassNotFoundException ex) {
            // Expected - do nothing
        }

        port = findFreePort();
        server = new Server(port);
        server.setHandler(generateServletContextHandler());

        server.start();
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

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_blocking_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(port)
                .headers(upstreamSpanInfo.getRight())
                .log().all()
            .when()
                .get(BLOCKING_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(BLOCKING_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_blocking_forward_endpoint_traced_correctly(boolean upstreamSendsSpan) {
        Pair<Span, Map<String, String>> upstreamSpanInfo = (upstreamSendsSpan)
                                                           ? generateUpstreamSpanHeaders()
                                                           : Pair.of((Span)null, Collections.<String, String>emptyMap());

        ExtractableResponse response =
            given()
                .baseUri("http://localhost")
                .port(port)
                .headers(upstreamSpanInfo.getRight())
                .log().all()
            .when()
                .get(BLOCKING_FORWARD_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(BLOCKING_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS * 2, upstreamSpanInfo.getLeft());
    }

    private Pair<Span, Map<String, String>> generateUpstreamSpanHeaders() {
        Span span = Span.newBuilder("upstreamSpan", Span.SpanPurpose.CLIENT).build();
        Map<String, String> headers = MapBuilder
            .builder(TraceHeaders.TRACE_ID, span.getTraceId())
            .put(TraceHeaders.SPAN_ID, span.getSpanId())
            .put(TraceHeaders.SPAN_NAME, span.getSpanName())
            .put(TraceHeaders.TRACE_SAMPLED, String.valueOf(span.isSampleable()))
            .build();

        return Pair.of(span, headers);
    }

    private void verifySingleSpanCompletedAndReturnedInResponse(ExtractableResponse response,
                                                                long expectedMinSpanDurationMillis,
                                                                Span expectedUpstreamSpan) {
        // We can have a race condition where the response is sent and we try to verify here before the servlet filter
        //      has had a chance to complete the span. Wait a few milliseconds to give the servlet filter time to
        //      finish.
        waitUntilSpanRecorderHasExpectedNumSpans(1);
        
        assertThat(spanRecorder.completedSpans).hasSize(1);
        Span completedSpan = spanRecorder.completedSpans.get(0);
        String traceIdFromResponse = response.header(TraceHeaders.TRACE_ID);
        assertThat(traceIdFromResponse).isNotNull();
        assertThat(completedSpan.getTraceId()).isEqualTo(traceIdFromResponse);

        assertThat(TimeUnit.NANOSECONDS.toMillis(completedSpan.getDurationNanos()))
            .isGreaterThanOrEqualTo(expectedMinSpanDurationMillis);

        if (expectedUpstreamSpan != null) {
            assertThat(completedSpan.getTraceId()).isEqualTo(expectedUpstreamSpan.getTraceId());
            assertThat(completedSpan.getParentSpanId()).isEqualTo(expectedUpstreamSpan.getSpanId());
        }
    }

    private void waitUntilSpanRecorderHasExpectedNumSpans(int expectedNumSpans) {
        long timeoutMillis = 5000;
        long startTimeMillis = System.currentTimeMillis();
        while (spanRecorder.completedSpans.size() < expectedNumSpans) {
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            long timeSinceStart = System.currentTimeMillis() - startTimeMillis;
            if (timeSinceStart > timeoutMillis) {
                throw new RuntimeException(
                    "spanRecorder did not have the expected number of spans after waiting "
                    + timeoutMillis + " milliseconds"
                );
            }
        }
    }

    public static class SpanRecorder implements SpanLifecycleListener {

        final List<Span> completedSpans = Collections.synchronizedList(new ArrayList<Span>());

        @Override
        public void spanStarted(Span span) { }

        @Override
        public void spanSampled(Span span) { }

        @Override
        public void spanCompleted(Span span) {
            completedSpans.add(span);
        }
    }

    public static int findFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private static final String BLOCKING_PATH = "/blocking";
    private static final String BLOCKING_RESULT = "blocking endpoint hit - " + UUID.randomUUID().toString();

    private static final String BLOCKING_FORWARD_PATH = "/blockingForward";

    private static final int SLEEP_TIME_MILLIS = 50;

    public static class BlockingServlet extends HttpServlet {

        public void doGet(
            HttpServletRequest request, HttpServletResponse response
        ) throws ServletException, IOException {
            sleepThread(SLEEP_TIME_MILLIS);
            response.getWriter().print(BLOCKING_RESULT);
            response.flushBuffer();
        }

    }

    public static class BlockingForwardServlet extends HttpServlet {

        public void doGet(
            HttpServletRequest request, HttpServletResponse response
        ) throws ServletException, IOException {
            sleepThread(SLEEP_TIME_MILLIS);
            request.getRequestDispatcher(BLOCKING_PATH).forward(request, response);
        }

    }

    private static Handler generateServletContextHandler() throws IOException {
        ServletHandler servletHandler = new ServletHandler();

        servletHandler.addServletWithMapping(BlockingServlet.class, BLOCKING_PATH);
        servletHandler.addServletWithMapping(BlockingForwardServlet.class, BLOCKING_FORWARD_PATH);
        servletHandler.addFilterWithMapping(RequestTracingFilter.class.getName(), "/*", Handler.ALL);

        Context context = new Context(null, null, null, servletHandler, null);
        context.setContextPath("/");
        return context;
    }

    private static void sleepThread(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
