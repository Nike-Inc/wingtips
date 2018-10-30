package com.nike.wingtips.jersey2sample.resource;

import com.nike.wingtips.servlet.RequestTracingFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;

/**
 * A set of example endpoints showing tracing working in a Jersey 2 app (in particular it shows {@link
 * RequestTracingFilter} starting and completing request spans in a variety of situations).
 *
 * <p>Note that this does not cover propagating tracing to downstream requests or subspans - it only shows the
 * automatic request span start and completion, and shows how log messages can be automatically tagged with tracing
 * info. It also shows how you can make tracing hop threads during async processing - see
 * {@link #getAsync(AsyncResponse)}.
 *
 * @author Nic Munroe
 */
@Path("/")
@SuppressWarnings({"WeakerAccess", "deprecation"})
public class SampleResource {

    public static final String SAMPLE_PATH_BASE = "/sample";

    public static final String SIMPLE_PATH = SAMPLE_PATH_BASE + "/simple";
    public static final String SIMPLE_RESULT = "simple endpoint hit - check logs for distributed tracing info";

    public static final String BLOCKING_PATH = SAMPLE_PATH_BASE + "/blocking";
    public static final String BLOCKING_RESULT = "blocking endpoint hit - check logs for distributed tracing info";

    public static final String ASYNC_PATH = SAMPLE_PATH_BASE + "/async";
    public static final String ASYNC_RESULT = "async endpoint hit - check logs for distributed tracing info";

    public static final String ASYNC_TIMEOUT_PATH = SAMPLE_PATH_BASE + "/async-timeout";
    public static final String ASYNC_ERROR_PATH = SAMPLE_PATH_BASE + "/async-error";

    public static final String PATH_PARAM_ENDPOINT_PATH_PREFIX = SAMPLE_PATH_BASE + "/path-param";
    public static final String PATH_PARAM_ENDPOINT_RESULT =
        "path param endpoint hit - check logs for distributed tracing info";

    public static final String WILDCARD_PATH_PREFIX = SAMPLE_PATH_BASE + "/wildcard";
    public static final String WILDCARD_RESULT = "wildcard endpoint hit - check logs for distributed tracing info";

    public static final long SLEEP_TIME_MILLIS = 100;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final Logger logger = LoggerFactory.getLogger(SampleResource.class);

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

    @GET
    @Path(PATH_PARAM_ENDPOINT_PATH_PREFIX + "/{somePathParam}")
    public String getPathParam(@PathParam("somePathParam") String somePathParam) {
        logger.info("Path param endpoint hit - somePathParam: {}", somePathParam);
        sleepThread(SLEEP_TIME_MILLIS);

        return PATH_PARAM_ENDPOINT_RESULT;
    }

    @GET
    @Path(WILDCARD_PATH_PREFIX + "/{restOfPath:.+}")
    public String getWildcard() {
        logger.info("Wildcard endpoint hit");
        sleepThread(SLEEP_TIME_MILLIS);

        return WILDCARD_RESULT;
    }

    @GET
    @Path(ASYNC_PATH)
    public void getAsync(@Suspended AsyncResponse asyncResponse) {
        logger.info("Async endpoint hit");

        executor.execute(runnableWithTracing(() -> {
            logger.info("Async endpoint async logic");
            sleepThread(SLEEP_TIME_MILLIS);
            asyncResponse.resume(ASYNC_RESULT);
        }));
    }

    @GET
    @Path(ASYNC_TIMEOUT_PATH)
    public void getAsyncTimeout(@Suspended AsyncResponse asyncResponse) {
        logger.info("Async timeout endpoint hit");

        asyncResponse.setTimeout(SLEEP_TIME_MILLIS, TimeUnit.MILLISECONDS);
    }

    @GET
    @Path(ASYNC_ERROR_PATH)
    public void getAsyncError(@Suspended AsyncResponse asyncResponse) {
        logger.info("Async error endpoint hit");

        sleepThread(SLEEP_TIME_MILLIS);
        asyncResponse.resume(new RuntimeException("Intentional exception by asyncError endpoint"));
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
