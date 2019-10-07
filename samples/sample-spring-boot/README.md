# Wingtips Sample Application - spring-boot

Wingtips is a distributed tracing solution for Java 7 and greater based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf).

This submodule contains a sample application based on Spring Boot that uses `WingtipsWithZipkinSpringBootConfiguration` 
from [`wingtips-zipkin2-spring-boot`](../../wingtips-zipkin2-spring-boot) to setup both Wingtips'
`RequestTracingFilter` (to automatically start and complete the overall request span for incoming requests) and 
Wingtips' Zipkin [integration](../../wingtips-zipkin2) (to send completed Wingtips spans to a
[Zipkin](http://zipkin.io/) server), all configured from the sample's `application.properties`. As always with 
`RequestTracingFilter`, if the incoming request contains tracing headers then they will be used as the parent span, 
otherwise a new trace will be started. 

This sample also shows the other half of the equation 
(the [Propagating Distributed Traces Across Network or Application Boundaries](../../README.md#propagating_traces) 
section of the main Wingtips readme). Specifically it shows how to use the interceptors from the 
[`wingtips-spring`](../../wingtips-spring) module to make Spring `RestTemplate` and `AsyncRestTemplate` HTTP client 
calls that automatically surround a downstream call in a separate subspan and propagate the tracing info in the 
downstream call request headers.

There are also a few examples showing how to make tracing state hop threads when you do asynchronous processing.

## Building and running the sample 
 
* Build the sample by running the `./buildSample.sh` script.
* Launch the sample by running the `./runSample.sh` script. It will bind to port 8080 by default. 
    * You can override the default port by passing in a system property to the run script, 
    e.g. to bind to port 8181: `./runSample.sh -Dserver.port=8181`
 
<a name="launching_zipkin"></a>
## Launching a Zipkin server for use with the sample

Part of what this sample application does is show how to integrate a Wingtips-enabled Spring Boot server with 
[Zipkin](http://zipkin.io/). In order to see the results of this integration you'll need a locally-running Zipkin
server. See the [Zipkin quickstart](http://zipkin.io/pages/quickstart) page to learn how to download and launch the 
Zipkin server - it should only take a few seconds.

Once your local Zipkin server is running and you've hit a few of the sample application's endpoints (and/or executed 
`VerifySampleEndpointsComponentTest`) you can open [http://localhost:9411](http://localhost:9411) in a browser and 
search for traces.

If you don't launch a locally-running Zipkin server the sample application will log messages about not being able to 
send spans to the Zipkin server, but will otherwise function normally.
 
## Things to try
 
All examples here assume the sample app is running on port 8080, so you would hit each path by going to 
`http://localhost:8080/[endpoint-path]`. It's recommended that you use a REST client like 
[Postman](https://www.getpostman.com/) for making the requests so you can easily specify HTTP method, payloads, headers, 
etc, and fully inspect the response.

Also note that all the following things to try are verified in a component test: `VerifySampleEndpointsComponentTest`. 
If you prefer to experiment via code you can run, debug, and otherwise explore that test. 

As you are doing the following you should check the logs that are output by the sample application and notice what is 
included in the log messages. In particular notice how you can search for a specific trace ID that came back in the
response headers and find all the relevant log message for that request in the logs. Additionally if you [launch
a local Zipkin server](#launching_zipkin) you can open [http://localhost:9411](http://localhost:9411) in a browser and
search for traces.  
 
* For all of the following things to try, you can specify `X-B3-TraceId` and `X-B3-SpanId` headers to cause the server
to use those values as parent span information. Try sending requests with and without these headers to see how it
affects the resulting server logs. If you are sending your own trace and span IDs you can also optionally send a 
`X-B3-Sampled` header with a value of `0` to disable the `[DISTRIBUTED_TRACING]` log for that request. Finally, this
sample is configured to treat `userid` and `altuserid` headers as "User ID Header Keys" and populate the Span's user
ID when one of those headers are found. 
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
`DeferredResult` to initiate the async processing.
* `GET /sample/async-future` - Similar to the `/sample/async` endpoint, but uses `CompletableFuture` instead of 
`DeferredResult` to initiate the async processing. 
* `GET /sample/async-error` - An async endpoint that sets the timeout for the request to 100 milliseconds 
and then fails to complete the request, causing the request to return a timeout error after that timeout period
passes. The `[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around `100000000` 
(100 milliseconds). You should see a log message output by the endpoint that is auto-tagged with the correct `traceId`.
* `GET /sample/span-info` - A blocking endpoint that waits for 100 milliseconds and then returns a JSON response 
payload that contains information about both the parent span that came in on the request headers (if any) and the 
endpoint span. This can be helpful to visualize what's going on when you send in tracing headers on the request
(essentially propagating your own tracing info to the sample server). The `[DISTRIBUTED_TRACING]` log message for this 
endpoint should have a `durationNanos` around `100000000` (100 milliseconds). You should see a log message output by 
the endpoint that is auto-tagged with the correct `traceId`.
* `GET /sample/nested-blocking-call` - A blocking endpoint that waits 100 milliseconds and then uses a `RestTemplate`
with tracing interceptor to make a HTTP client call to `/sample/span-info` that is automatically wrapped in a subspan
and automatically propagates the tracing info on the downstream call. The result of `/sample/span-info` is returned.
The total call time should be around 200 milliseconds (100 for each of the endpoints that are called). There should
be three `[DISTRIBUTED_TRACING]` log messages - one for the innermost `/sample/span-info` endpoint, one for the subspan
surrounding the `RestTemplate` HTTP client call, and one for the outermost `/sample/nested-blocking-call` endpoint. 
You should see several log messages auto-tagged with the correct `traceId` across both endpoints.
* `GET /sample/nested-async-call` - Similar to the `/sample/nested-blocking-call` endpoint described above, except
it is fully async and uses `AsyncRestTemplate` instead of the blocking `RestTemplate` to make the HTTP client call. 
All the notes from the `/sample/nested-blocking-call` description about timing and log messages apply to this 
endpoint as well - in particular note how all the different log messages are tagged with the trace ID despite the
request hopping threads several times.

## More Info

See the [base project README.md](../../README.md) and Wingtips repository source code and javadocs for all further 
information.

## License

Wingtips is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
