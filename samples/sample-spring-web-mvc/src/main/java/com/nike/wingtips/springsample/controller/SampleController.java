package com.nike.wingtips.springsample.controller;

import com.nike.wingtips.servlet.RequestTracingFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.callableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperJava7.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;

/**
 * A set of example endpoints showing tracing working in a Spring MVC app (in particular it shows {@link
 * RequestTracingFilter} starting and completing request spans in a variety of situations).
 *
 * <p>Note that this does not cover propagating tracing to downstream requests or subspans - it only shows the
 * automatic request span start and completion, and shows how log messages can be automatically tagged with tracing
 * info. It also shows how you can make tracing hop threads during async processing - see
 * {@link #getAsyncDeferredResult()}, {@link #getAsyncCallable()}, and {@link #getAsyncCompletableFuture()}.
 */
@Controller
@RequestMapping("/")
@SuppressWarnings({"WeakerAccess", "deprecation"})
public class SampleController {

    public static final String SAMPLE_PATH_BASE = "/sample";

    public static final String SIMPLE_PATH = SAMPLE_PATH_BASE + "/simple";
    public static final String SIMPLE_RESULT = "simple endpoint hit - check logs for distributed tracing info";

    public static final String BLOCKING_PATH = SAMPLE_PATH_BASE + "/blocking";
    public static final String BLOCKING_RESULT = "blocking endpoint hit - check logs for distributed tracing info";

    public static final String ASYNC_DEFERRED_RESULT_PATH = SAMPLE_PATH_BASE + "/async";
    public static final String ASYNC_DEFERRED_RESULT_PAYLOAD =
        "async endpoint hit (DeferredResult) - check logs for distributed tracing info";

    public static final String ASYNC_CALLABLE_PATH = SAMPLE_PATH_BASE + "/async-callable";
    public static final String ASYNC_CALLABLE_RESULT =
        "async endpoint hit (Callable) - check logs for distributed tracing info";

    public static final String ASYNC_FUTURE_PATH = SAMPLE_PATH_BASE + "/async-future";
    public static final String ASYNC_FUTURE_RESULT =
        "async endpoint hit (CompletableFuture) - check logs for distributed tracing info";

    public static final String ASYNC_ERROR_PATH = SAMPLE_PATH_BASE + "/async-error";

    public static final long SLEEP_TIME_MILLIS = 100;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final Logger logger = LoggerFactory.getLogger(SampleController.class);

    @GetMapping(path = SIMPLE_PATH)
    @ResponseBody
    @SuppressWarnings("unused")
    public String getSimple() {
        return SIMPLE_RESULT;
    }

    @GetMapping(path = BLOCKING_PATH)
    @ResponseBody
    @SuppressWarnings("unused")
    public String getBlocking() {
        logger.info("Blocking endpoint hit");
        sleepThread(SLEEP_TIME_MILLIS);

        return BLOCKING_RESULT;
    }

    @GetMapping(path = ASYNC_DEFERRED_RESULT_PATH)
    @ResponseBody
    @SuppressWarnings("unused")
    public DeferredResult<String> getAsyncDeferredResult() {
        logger.info("Async endpoint hit (DeferredResult)");

        DeferredResult<String> asyncResponse = new DeferredResult<>();

        executor.execute(runnableWithTracing(() -> {
            logger.info("Async endpoint (DeferredResult) async logic");
            sleepThread(SLEEP_TIME_MILLIS);
            asyncResponse.setResult(ASYNC_DEFERRED_RESULT_PAYLOAD);
        }));

        return asyncResponse;
    }

    @GetMapping(path = ASYNC_CALLABLE_PATH)
    @ResponseBody
    @SuppressWarnings("unused")
    public Callable<String> getAsyncCallable() {
        logger.info("Async endpoint hit (Callable)");

        return callableWithTracing(() -> {
            logger.info("Async endpoint (Callable) async logic");
            sleepThread(SLEEP_TIME_MILLIS);
            return ASYNC_CALLABLE_RESULT;
        });
    }

    @GetMapping(path = ASYNC_FUTURE_PATH)
    @ResponseBody
    @SuppressWarnings("unused")
    public CompletableFuture<String> getAsyncCompletableFuture() {
        logger.info("Async endpoint hit (CompletableFuture)");

        return CompletableFuture.supplyAsync(supplierWithTracing(
            () -> {
                logger.info("Async endpoint (CompletableFuture) async logic");
                sleepThread(SLEEP_TIME_MILLIS);
                return ASYNC_FUTURE_RESULT;
            }),
            executor
        );
    }

    @GetMapping(path = ASYNC_ERROR_PATH)
    @ResponseBody
    @SuppressWarnings("unused")
    public DeferredResult<String> getAsyncError() {
        logger.info("Async error endpoint hit");

        return new DeferredResult<>(SLEEP_TIME_MILLIS);
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
