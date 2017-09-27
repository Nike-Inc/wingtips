# Wingtips Sample Application - spring-web-mvc

Wingtips is a distributed tracing solution for Java 7 and greater based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf).

This submodule contains a sample application based on Spring Web MVC that integrates Wingtips' `RequestTracingFilter` to
automatically start and complete the overall request span for incoming requests. If the incoming request contains 
tracing headers then they will be used as the parent span, otherwise a new trace will be started. Note that this sample 
only covers the server-receiving-requests side of the tracing equation. For the other half please see the 
[Propagating Distributed Traces Across Network or Application Boundaries](../../README.md#propagating_traces) section 
of the main Wingtips readme.
 
* Build the sample by running the `./buildSample.sh` script.
* Launch the sample by running the `./runSample.sh` script. It will bind to port 8080 by default. 
    * You can override the default port by passing in a system property to the run script, 
    e.g. to bind to port 8181: `./runSample.sh -DspringSample.server.port=8181`
 
## Things to try
 
All examples here assume the sample app is running on port 8080, so you would hit each path by going to 
`http://localhost:8080/[endpoint-path]`. It's recommended that you use a REST client like 
[Postman](https://www.getpostman.com/) for making the requests so you can easily specify HTTP method, payloads, headers, 
etc, and fully inspect the response.

Also note that all the following things to try are verified in a component test: `VerifySampleEndpointsComponentTest`. 
If you prefer to experiment via code you can run, debug, and otherwise explore that test. 

As you are doing the following you should check the logs that are output by the sample application and notice what is 
included in the log messages. In particular notice how you can search for a specific trace ID that came back in the
response headers and find all the relevant log message for that request in the logs. 
 
* For all of the following things to try, you can specify `X-B3-TraceId` and `X-B3-SpanId` headers to cause the server
to use those values as parent span information. Try sending requests with and without these headers to see how it
affects the resulting server logs. If you are sending your own trace and span IDs you can also optionally send a 
`X-B3-Sampled` header with a value of `0` to disable the `[DISTRIBUTED_TRACING]` log for that request. 
* `GET /sample/simple` - A basic blocking endpoint that returns as quickly as possible. The `[DISTRIBUTED_TRACING]` 
log message for this endpoint should have very short `durationNanos` - note that because the duration is nanosecond
precision you can accurately time endpoints that return in well under 1 millisecond. You should see a log message 
output by the endpoint that is auto-tagged with the correct `traceId`.
* `GET /sample/blocking` - A blocking endpoint that waits for 100 milliseconds before returning. The 
`[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around `100000000` (100 
milliseconds). You should see a log message output by the endpoint that is auto-tagged with the correct `traceId`.
* `GET /sample/async` - An async endpoint (using `DeferredResult`) that waits for 100 milliseconds on another thread before 
completing the request. The `[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around 
`100000000` (100 milliseconds). You should see two log messages output by the endpoint that are auto-tagged with the 
correct `traceId` - one for the endpoint before async processing is started, and another on the async thread before
the request is completed.
* `GET /sample/async-callable` - Similar to the `/sample/async` endpoint, but uses `Callable` instead of 
`DeferredResult` to initiate the async processing. The `[DISTRIBUTED_TRACING]` log message for this endpoint should 
have a `durationNanos` around `100000000` (100 milliseconds). You should see two log messages output by the endpoint 
that are auto-tagged with the correct `traceId` - one for the endpoint before async processing is started, and another 
on the async thread before the request is completed.
* `GET /sample/async-future` - Similar to the `/sample/async` endpoint, but uses `CompletableFuture` instead of 
`DeferredResult` to initiate the async processing. The `[DISTRIBUTED_TRACING]` log message for this endpoint should
have a `durationNanos` around `100000000` (100 milliseconds). You should see two log messages output by the endpoint
that are auto-tagged with the correct `traceId` - one for the endpoint before async processing is started, and another
on the async thread before the request is completed.
* `GET /sample/async-error` - An async endpoint that sets the timeout for the request to 100 milliseconds 
and then fails to complete the request, causing the request to return a timeout error after that timeout period
passes. The `[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around `100000000` 
(100 milliseconds). You should see a log message output by the endpoint that is auto-tagged with the correct `traceId`.

## More Info

See the [base project README.md](../../README.md) and Wingtips repository source code and javadocs for all further 
information.

## License

Wingtips is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
