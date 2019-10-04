# Wingtips Sample Application - spring-boot2-webflux

Wingtips is a distributed tracing solution for Java 7 and greater based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf).

This submodule contains a sample application based on Spring Boot 2 with WebFlux (not Web MVC) that uses 
`WingtipsWithZipkinSpringBoot2WebfluxConfiguration` from 
[`wingtips-zipkin2-spring-boot2-webflux`](../../wingtips-zipkin2-spring-boot2-webflux) to setup both Wingtips'
`WingtipsSpringWebfluxWebFilter` (to automatically start and complete the overall request span for incoming requests) 
and Wingtips' Zipkin [integration](../../wingtips-zipkin2) (to send completed Wingtips spans to a
[Zipkin](http://zipkin.io/) server), all configured from the sample's `application.properties`. As always with 
`WingtipsSpringWebfluxWebFilter`, if the incoming request contains tracing headers then they will be used as the 
parent span, otherwise a new trace will be started. 

This sample also shows the other half of the equation 
(the [Propagating Distributed Traces Across Network or Application Boundaries](../../README.md#propagating_traces) 
section of the main Wingtips readme). Specifically it shows how to use the interceptor from the 
[`wingtips-spring-webflux`](../../wingtips-spring-webflux) module (`WingtipsSpringWebfluxExchangeFilterFunction`) to 
make Spring's reactive HTTP client `WebClient` automatically surround HTTP client requests in a separate subspan and 
propagate the tracing info in the downstream call request headers.

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

If you don't launch a locally-running Zipkin server then the sample application will still function normally - you 
just won't be able to visualize the span data.
 
## Things to try
 
All examples here assume the sample app is running on port 8080, so you would hit each path by going to 
`http://localhost:8080/[endpoint-path]`. It's recommended that you use a REST client like 
[Postman](https://www.getpostman.com/) for making the requests so you can easily specify HTTP method, payloads, 
headers, etc, and fully inspect the response.

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
* `GET /sample/mono` - A `Mono` endpoint where the `Mono` delays for 100 milliseconds before supplying the result. The 
`[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around `100000000` (100 
milliseconds). You should see a log message output by the endpoint that is auto-tagged with the correct `traceId`.
* `GET /sample/flux` - A `Flux` endpoint where the `Flux` delays for 100 milliseconds total to supply all the results. 
The `[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around `100000000` (100 
milliseconds). You should see a log message output by the endpoint that is auto-tagged with the correct `traceId`.
* `GET /sample/router-function` - A `RouterFunction` endpoint where the `Mono` delays for 100 milliseconds before 
supplying the result. The `[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around 
`100000000` (100 milliseconds). You should see a log message output by the endpoint that is auto-tagged with the 
correct `traceId`.
* `GET /sample/async-error` - A `Mono` endpoint where the `Mono` completes with an error. The `[DISTRIBUTED_TRACING]` 
log message for this endpoint should have a `durationNanos` around `100000000` (100 milliseconds). You should see a 
log message output by the endpoint that is auto-tagged with the correct `traceId`, along with another auto-tagged
one with the error.
* `GET /sample/async-timeout` - A `Mono` endpoint where the `Mono` is set to timeout after 100 milliseconds. 
The `[DISTRIBUTED_TRACING]` log message for this endpoint should have a `durationNanos` around `100000000` (100 
milliseconds). You should see a log message output by the endpoint that is auto-tagged with the correct `traceId`, 
along with another auto-tagged one with the error.
* `GET /sample/async-future` - An async endpoint (using `CompletableFuture`) that waits for 100 milliseconds on 
another thread before completing the request. The `[DISTRIBUTED_TRACING]` log message for this endpoint should have 
a `durationNanos` around `100000000` (100 milliseconds). You should see two log messages output by the endpoint that 
are auto-tagged with the correct `traceId` - one for the endpoint before async processing is started, and another on 
the async thread before the request is completed.
* `GET /sample/span-info` - A `Mono` endpoint that waits for 100 milliseconds and then returns a JSON response 
payload that contains information about both the parent span that came in on the request headers (if any) and the 
endpoint span. This can be helpful to mentally visualize what's going on when you send in tracing headers on the request
(essentially propagating your own tracing info to the sample server). The `[DISTRIBUTED_TRACING]` log message for this 
endpoint should have a `durationNanos` around `100000000` (100 milliseconds). You should see multiple log messages 
output by the endpoint that are auto-tagged with the correct `traceId`. If you explore the code for this endpoint 
you'll see how the correct `TracingState` for the request can be extracted from the thread when the endpoint is 
executed, the `ServerWebExchange` attributes, and/or the `Mono`'s `Context`.
* `GET /sample/nested-webclient-call` - A `Mono` endpoint that waits 100 milliseconds and then uses a `WebClient`
with tracing interceptor to make a HTTP client call to `/sample/span-info` that is automatically wrapped in a subspan
and automatically propagates the tracing info on the downstream call. The result of `/sample/span-info` is returned.
The total call time should be around 200 milliseconds (100 for each of the endpoints that are called). There should
be three `[DISTRIBUTED_TRACING]` log messages - one for the innermost `/sample/span-info` endpoint, one for the subspan
surrounding the `WebClient` HTTP client call, and one for the outermost `/sample/nested-webclient-call` endpoint. 
You should see several log messages auto-tagged with the correct `traceId` across both endpoints. Note how all the 
different log messages are tagged with the trace ID despite the request hopping threads several times.
* `GET /sample/path-param/{somePathParam}` - Similar to the `GET /sample/mono` endpoint, except it has a path parameter
in the path. The span name and `http.route` tag will use the low-cardinality path template, while the `http.path` and 
`http.url` tags will contain the full high-cardinality path.
* `GET /sample/wildcard/**` - Similar to the `GET /sample/mono` endpoint, except it has a wildcard in the path. 
The span name and `http.route` tag will use the low-cardinality path template, while the `http.path` and `http.url` 
tags will contain the full high-cardinality path.

## More Info

See the [base project README.md](../../README.md) and Wingtips repository source code and javadocs for all further 
information.

## License

Wingtips is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
