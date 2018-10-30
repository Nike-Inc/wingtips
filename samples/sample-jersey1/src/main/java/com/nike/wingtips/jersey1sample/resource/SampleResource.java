package com.nike.wingtips.jersey1sample.resource;

import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.util.TracingState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;

/**
 * A set of example endpoints showing tracing working in a Jersey 1 app (in particular it shows {@link
 * RequestTracingFilter} starting and completing request spans in a variety of situations).
 *
 * <p>Note that this does not cover propagating tracing to downstream requests or subspans - it only shows the
 * automatic request span start and completion, and shows how log messages can be automatically tagged with tracing
 * info. It also shows how you can make tracing hop threads during async processing - see {@link
 * SampleAsyncForwardServlet} and {@link SampleAsyncServlet}.
 *
 * @author Nic Munroe
 */
@Path("/")
@SuppressWarnings("WeakerAccess")
public class SampleResource {

    public static final String SAMPLE_PATH_BASE = "/sample";

    public static final String SIMPLE_PATH = SAMPLE_PATH_BASE + "/simple";
    public static final String SIMPLE_RESULT = "simple endpoint hit - check logs for distributed tracing info";

    public static final String BLOCKING_PATH = SAMPLE_PATH_BASE + "/blocking";
    public static final String BLOCKING_RESULT = "blocking endpoint hit - check logs for distributed tracing info";

    public static final String ASYNC_PATH = SAMPLE_PATH_BASE + "/async";
    public static final String ASYNC_RESULT = "async endpoint hit - check logs for distributed tracing info";

    public static final String BLOCKING_FORWARD_PATH = SAMPLE_PATH_BASE + "/blocking-forward";
    public static final String ASYNC_FORWARD_PATH = SAMPLE_PATH_BASE + "/async-forward";
    public static final String ASYNC_TIMEOUT_PATH = SAMPLE_PATH_BASE + "/async-timeout";
    public static final String ASYNC_ERROR_PATH = SAMPLE_PATH_BASE + "/async-error";

    public static final long SLEEP_TIME_MILLIS = 100;
    
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final Logger logger = LoggerFactory.getLogger(SampleResource.class);

    private static final String ASYNC_TRACING_STATE_REQUEST_ATTR_KEY = "AsyncTracingState";

    @GET
    @Path(SIMPLE_PATH)
    public String getSimple() {
        logger.info("Simple endpoint hit");
        return SIMPLE_RESULT;
    }

    @GET
    @Path(BLOCKING_PATH)
    public String getBlocking() {
        logger.info("Blocking endpoint hit");
        sleepThread(SLEEP_TIME_MILLIS);

        return BLOCKING_RESULT;
    }

    @SuppressWarnings("deprecation")
    public static class SampleAsyncServlet extends HttpServlet {

        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            TracingState asyncTracingState = (TracingState) request.getAttribute(ASYNC_TRACING_STATE_REQUEST_ATTR_KEY);
            if (asyncTracingState != null) {
                runnableWithTracing(
                    () -> doGetInternal(request, response),
                    asyncTracingState
                ).run();
            }
            else {
                doGetInternal(request, response);
            }
        }

        protected void doGetInternal(HttpServletRequest request, HttpServletResponse response) {
            logger.info("Async endpoint hit");
            AsyncContext asyncContext = request.startAsync(request, response);

            executor.execute(runnableWithTracing(() -> {
                try {
                    logger.info("Async endpoint async logic");
                    sleepThread(SLEEP_TIME_MILLIS);
                    asyncContext.getResponse().getWriter().print(ASYNC_RESULT);
                    asyncContext.complete();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }

    }

    public static class SampleBlockingForwardServlet extends HttpServlet {

        public void doGet(
            HttpServletRequest request, HttpServletResponse response
        ) throws ServletException, IOException {
            logger.info("Blocking forward endpoint hit");
            sleepThread(SLEEP_TIME_MILLIS);
            request.getServletContext().getRequestDispatcher(BLOCKING_PATH).forward(request, response);
        }

    }
    
    @SuppressWarnings("deprecation")
    public static class SampleAsyncForwardServlet extends HttpServlet {

        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            logger.info("Async forward endpoint hit");
            AsyncContext asyncContext = request.startAsync(request, response);

            TracingState origTracingState = TracingState.getCurrentThreadTracingState();
            executor.execute(runnableWithTracing(
                () -> {
                    logger.info("Async forward endpoint async logic");
                    asyncContext.getRequest().setAttribute(ASYNC_TRACING_STATE_REQUEST_ATTR_KEY, origTracingState);
                    sleepThread(SLEEP_TIME_MILLIS);
                    asyncContext.dispatch(ASYNC_PATH);
                },
                origTracingState
            ));
        }

    }

    public static class SampleAsyncTimeoutServlet extends HttpServlet {

        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            if (DispatcherType.ERROR.equals(request.getDispatcherType()))
                return;

            logger.info("Async timeout endpoint hit");

            AsyncContext asyncContext = request.startAsync(request, response);
            asyncContext.setTimeout(SLEEP_TIME_MILLIS);
        }

    }

    public static class SampleAsyncErrorServlet extends HttpServlet {

        public static final String EXCEPTION_MESSAGE = "Intentional exception by asyncError endpoint";

        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            if (DispatcherType.ERROR.equals(request.getDispatcherType()))
                return;

            logger.info("Async error endpoint hit");
            
            request.startAsync(request, response);
            sleepThread(SLEEP_TIME_MILLIS);
            throw new RuntimeException(EXCEPTION_MESSAGE);
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
