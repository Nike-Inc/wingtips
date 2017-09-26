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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.restassured.response.ExtractableResponse;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component test to verify that {@link RequestTracingFilter} works as expected when deployed to a real running server.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class RequestTracingFilterComponentTest {

    private static int port;
    private static Server server;

    private SpanRecorder spanRecorder;

    @BeforeClass
    public static void beforeClass() throws Exception {
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
    public void verify_async_endpoint_traced_correctly(boolean upstreamSendsSpan) {
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
                .get(ASYNC_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(ASYNC_RESULT);
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

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_forward_endpoint_traced_correctly(boolean upstreamSendsSpan) {
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
                .get(ASYNC_FORWARD_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(ASYNC_RESULT);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS * 2, upstreamSpanInfo.getLeft());
    }

    @DataProvider(value = {
        "true",
        "false"
    })
    @Test
    public void verify_async_error_endpoint_traced_correctly(boolean upstreamSendsSpan) {
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
                .get(ASYNC_ERROR_PATH)
            .then()
                .log().all()
                .extract();

        assertThat(response.statusCode()).isEqualTo(500);
        verifySingleSpanCompletedAndReturnedInResponse(response, SLEEP_TIME_MILLIS, upstreamSpanInfo.getLeft());
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

    public static int findFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private static ServletContextHandler generateServletContextHandler() throws IOException {
        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");

        contextHandler.addServlet(BlockingServlet.class, BLOCKING_PATH);
        contextHandler.addServlet(AsyncServlet.class, ASYNC_PATH);
        contextHandler.addServlet(BlockingForwardServlet.class, BLOCKING_FORWARD_PATH);
        contextHandler.addServlet(AsyncForwardServlet.class, ASYNC_FORWARD_PATH);
        contextHandler.addServlet(AsyncErrorServlet.class, ASYNC_ERROR_PATH);
        contextHandler.addFilter(RequestTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        return contextHandler;
    }

    private static final String BLOCKING_PATH = "/blocking";
    private static final String BLOCKING_RESULT = "blocking endpoint hit - " + UUID.randomUUID().toString();

    private static final String ASYNC_PATH = "/async";
    private static final String ASYNC_RESULT = "async endpoint hit - " + UUID.randomUUID().toString();

    private static final String BLOCKING_FORWARD_PATH = "/blockingForward";
    private static final String ASYNC_FORWARD_PATH = "/asyncForward";
    private static final String ASYNC_ERROR_PATH = "/asyncError";

    private static final int SLEEP_TIME_MILLIS = 50;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static class BlockingServlet extends HttpServlet {

        public void doGet(
            HttpServletRequest request, HttpServletResponse response
        ) throws ServletException, IOException {
            sleepThread(SLEEP_TIME_MILLIS);
            response.getWriter().print(BLOCKING_RESULT);
            response.flushBuffer();
        }

    }

    public static class AsyncServlet extends HttpServlet {

        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            final AsyncContext asyncContext = request.startAsync(request, response);

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        sleepThread(SLEEP_TIME_MILLIS);
                        asyncContext.getResponse().getWriter().print(ASYNC_RESULT);
                        asyncContext.complete();
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

    }

    public static class BlockingForwardServlet extends HttpServlet {

        public void doGet(
            HttpServletRequest request, HttpServletResponse response
        ) throws ServletException, IOException {
            sleepThread(SLEEP_TIME_MILLIS);
            request.getServletContext().getRequestDispatcher(BLOCKING_PATH).forward(request, response);
        }

    }

    public static class AsyncForwardServlet extends HttpServlet {

        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            final AsyncContext asyncContext = request.startAsync(request, response);

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    sleepThread(SLEEP_TIME_MILLIS);
                    asyncContext.dispatch(ASYNC_PATH);
                }
            });
        }

    }

    public static class AsyncErrorServlet extends HttpServlet {

        public void doGet(HttpServletRequest request, final HttpServletResponse response) {
            if (DispatcherType.ERROR.equals(request.getDispatcherType()))
                return;

            final AsyncContext asyncContext = request.startAsync(request, response);
            asyncContext.setTimeout(SLEEP_TIME_MILLIS);
        }

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
