<img src="wingtips_logo.png" />

# Wingtips - Give Your Distributed Systems a Dapper Footprint

[ ![Download](https://api.bintray.com/packages/nike/maven/wingtips/images/download.svg) ](https://bintray.com/nike/maven/wingtips/_latestVersion)
[![][travis img]][travis]
[![][license img]][license]

Wingtips is a distributed tracing solution for Java 7 and greater based on the [Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

There are a few modules associated with this project:

* [wingtips-core](wingtips-core/README.md) - The core library providing the majority of the distributed tracing functionality.
* [wingtips-servlet-api](wingtips-servlet-api/README.md) - A plugin for Servlet-based applications for integrating distributed tracing with a simple Servlet Filter.
* [wingtips-zipkin](wingtips-zipkin/README.md) - A plugin providing easy Zipkin integration by converting Wingtips spans to Zipkin spans and sending them to a Zipkin server.

## Table of Contents

* [Overview](#overview)
	* [What is a Distributed Trace Made Of?](#trace_and_span_anatomy) 
* [Quickstart and Usage](#quickstart)
	* [Generic Application Pseudo-Code](#generic_pseudo_code)
	* [Generic Application Pseudo-Code Explanation](#generic_pseudo_code_info)
		* [Is your application running in a Servlet-based framework?](#servlet_filter_info)
	* [Output and Logging](#output_and_logging)
		* [Automatically attaching trace information to all log messages](#mdc_info)
	* [Nested Sub-Spans and the Span Stack](#sub_spans)
		* [Using sub-spans to surround downstream calls](#sub_spans_for_downstream_calls)
	* [Propagating Distributed Traces Across Network or Application Boundaries](#propagating_traces)
	* [Adjusting Behavior and Execution Options](#adjusting_behavior)
		* [Sampling](#sampling)
		* [Notification of span lifecycle events](#span_lifecycle_events)
		* [Changing serialized representation of Spans for the logs](#logging_span_representation)
* [Usage in Reactive Asynchronous Nonblocking Scenarios](#async_usage)
* [Using Distributed Tracing to Help with Debugging Issues/Errors/Problems](#using_dtracing_for_errors)
* [Custom Annotations](#custom_annotations)
* [Integrating With Other Distributed Tracing Tools](#integrating_with_other_dtrace_tools)
* [License](#license)

<a name="overview"></a> 
## Overview

Distributed tracing is a mechanism to track requests through a network of distributed systems in order to create transparency and shed light on the sometimes complex interactions and behavior of those systems. For example in a cloud-based microservice architecture a single request can touch dozens or hundreds of servers as it spreads out in a tree-like fashion. Without distributed tracing it would be very difficult to identify which part(s) of such a complex system were causing performance issues - either in general or for that request in particular. 

Distributed tracing provides the capability for near-realtime monitoring *or* historical analysis of interactions between servers given the necessary tools to collect and interpret the traces. It allows you to easily see where applications are spending their time - e.g. is it mostly in-application work, or is it mostly waiting for downstream network calls?

Distributed tracing can also be used in some cases for error debugging and problem investigations with some caveats. See [the relevant section](#using_dtracing_for_errors) for more information on this topic.

<a name="trace_and_span_anatomy"></a>
### What is a Distributed Trace Made Of?

* Every distributed trace contains a TraceID that represents the entire request across all servers/microservices it touches. These TraceIDs are generated as probabilistically unique 64-bit integers (longs).
* Each unit of work that you want to track in the distributed trace is defined as a Span. Spans are usually broken down into overall-request-time for a given request in a given server/microservice, and downstream-call-time for each downstream call made for that request from the server/microservice. Spans are identified by a SpanID, which is also a pseudorandomly generated 64-bit long.
* Spans can have Parent Spans, which is how we build a tree of the request's behavior as it branches across server boundaries. The ParentSpanID is set to the parent Span's SpanID.
* All Spans contain the TraceID of the overall trace they are attached to, whether or not they have Parent Spans.
* Spans include a SpanName, which is a more human readable indication of what the span was, e.g. `GET_/some/endpoint` for the overall span for a REST request, or `downstream-POST_https://otherservice.com/other/endpoint` for the span performing a downstream call to another service.
* Spans contain timing info - a start timestamp and a duration value.

See the [Output and Logging section](#output_and_logging) for an example of what a span looks like when it is logged.	

<a name="quickstart"></a> 
## Quickstart and Usage

<a name="generic_pseudo_code"></a> 
### Generic Application Pseudo-Code

*NOTE: The following pseudo-code only applies to thread-per-request frameworks and scenarios. For asynchronous non-blocking scenarios see [this section](#async_usage).*


```java
import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

// ======As early in the request cycle as possible======
try {
	// Determine if a parent span exists by inspecting the request (e.g. request headers)
	Span parentSpan = extractParentSpanFromRequest(request);
	
	// Start the overall request span (which becomes the "current span" for this thread unless/until a sub-span is created)
	if (parentSpan == null)
		Tracer.getInstance().startRequestWithRootSpan("newRequestSpanName");
	else
		Tracer.getInstance().startRequestWithChildSpan(parentSpan, "newRequestSpanName");
		
	// It's recommended that you include the trace ID of the overall request span in the response headers
	addTraceIdToResponseHeaders(response, Tracer.getInstance().getCurrentSpan());
		
	// Execute the normal request logic	
	doRequestLogic();	
}
finally {
	// ======As late in the request/response cycle as possible======
	Tracer.getInstance().completeRequestSpan(); // Completes the overall request span and logs it to SLF4J
}
```

<a name="generic_pseudo_code_info"></a> 
### Generic Application Pseudo-Code Explanation
In a typical usage scenario you'll want to call one of the `Tracer.getInstance().startRequest...()` methods as soon as possible when a request enters your application, and you'll want to call `Tracer.getInstance().completeRequestSpan()` as late as possible in the request/response cycle. In between these two calls the span that was started (the "overall-request-span") is considered the "current span" for this thread and can be retrieved if necessary by calling `Tracer.getInstance().getCurrentSpan()`.

The `extractParentSpanFromRequest()` method is potentially different for different applications, however for HTTP-based frameworks the pattern is usually the same - look for and extract distributed-tracing-related information from the HTTP headers and use that information to create a parent span. There is a utility method that performs this work for you: `HttpRequestTracingUtils.fromRequestWithHeaders(RequestWithHeaders, List<String>)`. You simply need to provide the HTTP request wrapped by an implementation of `RequestWithHeaders` and the list of user ID header keys for your application (if any) and it will do the rest using the standard distributed tracing header key constants found in `TraceHeaders`. See the javadocs for those classes and methods for more information and usage instructions.

**NOTE:** Given the thread-local nature of this library you'll want to make sure the span completion call is in a finally block or otherwise guaranteed to be called no matter what (even if the request fails with an error) to prevent problems when subsequent requests are processed on the same thread. The `Tracer` class does its best to recover from incorrect thread usage scenarios and log information about what happened but the best solution is to prevent the problems from occurring in the first place.
 
<a name="servlet_filter_info"></a>  
#### Is your application running in a Servlet-based framework?

If your application is running in a Servlet environment (e.g. Spring MVC, Jersey, raw Servlets, etc) then this entire lifecycle can be handled by a Servlet `Filter`. We've created one for you that's ready to drop in and go - see the [wingtips-servlet-api](wingtips-servlet-api/README.md) Wingtips plugin module library for details. That plugin module is also a good resource to see how the code for a production-ready implementation of this library might look.
 
<a name="output_and_logging"></a>  
### Output and Logging

When `Tracer` completes a span it will log it to a SLF4J logger named `VALID_WINGTIPS_SPANS` so you can segregate your span information into a separate file if desired. The following is an example of log output for a valid span (in this case it is a root span because it does not have a parent span ID):

```
14:02:26.029 [main] INFO  VALID_WINGTIPS_SPANS - [DISTRIBUTED_TRACING] {"traceId":"8605610726410546456","parentSpanId":"null","spanId":"219182545067377576","spanName":"somespan","sampleable":"true","userId":"null","startTimeEpochMicros":"1445720545485958","durationNanos":"543516000"}
```

 If an invalid span is detected due to incorrect usage of `Tracer` then the invalid span will be logged to a SLF4J logger named `INVALID_WINGTIPS_SPANS`. These specially-named loggers will not be used for any other purpose.

<a name="mdc_info"></a> 
#### Automatically attaching trace information to all log messages

In addition to the logs this class outputs for completed spans it puts the trace ID and span JSON for the "current" span into the SLF4J [MDC](http://www.slf4j.org/manual.html#mdc) so that all your logs can be tagged with the current span's trace ID and/or full JSON. To utilize this you would need to add `%X{traceId}` and/or `%X{spanJson}` to your log pattern (*NOTE: this only works with SLF4J frameworks that support MDC, e.g. Logback and Log4j*). This causes *all* log messages, including ones that come from third party libraries and have no knowledge of distributed tracing, to be output with the current span's tracing information.

Here is an example [Logback pattern](http://logback.qos.ch/manual/layouts.html) utilizing the tracing MDC info:

```
traceId=%X{traceId} %date{HH:mm:ss.SSS} %-5level [%thread] %logger - %m%n
```

And here is what a log message output would look like when using this pattern:

```
traceId=5911002846273424396 14:43:53.483 INFO  [main] com.foo.Bar - important log message
```

***This is one of the primary features and benefits of Wingtips - if you utilize this MDC feature then you'll be able to trivially collect all log messages related to a specific request across all services it touched even if some of those messages came from third party libraries.***

A [Log4j pattern](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html) would look similar - in particular `%X{traceId}` to access the trace ID in the MDC is identical.

#### Changing output format

See [this section](#logging_span_representation) of this readme for information on how to change the serialization representation when logging completed spans (i.e. if you want spans to be serialized to a key/value string rather than JSON).

<a name="sub_spans"></a>
### Nested Sub-Spans and the Span Stack 

The span information associated with a thread is modeled as a stack, so it's possible to have nested spans inside the overall "request span" described previously. These nested spans are referred to as "sub-spans". So while the overall request span tracks the work done for the overall request, you can use sub-spans to mark work that you want tracked separately from the overall request. You can start and complete sub-spans using the `Tracer` sub-span methods: `Tracer.startSubSpan(String)` and `Tracer.completeSubSpan()`. 

These nested sub-spans are pushed onto the span stack associated with the current thread and you can have them as deeply nested as you want, but just like with the overall request span you'll want to make sure the completion method is called in a finally block or otherwise guaranteed to be executed even if an error occurs.

Each call to `Tracer.startSubSpan(String)` causes the "current span" to become the new sub-span's parent, and causes the new sub-span to become the "current span" by pushing it onto the span stack. Each call to `Tracer.completeSubSpan()` does the reverse by popping the current span off the span stack and completing and logging it, thus causing its parent to become the current span again.

**NOTE:** Sub-spans must be perfectly nested on any given thread - you must complete a sub-span before its parent can be completed. This is not generally an issue in thread-per-request scenarios due to the synchronous serial nature of the thread-per-request environment. It can fall apart in more complex asynchronous scenarios where multiple threads are performing work for a request at the same time. In those situations you are in the territory described by the [reactive/asynchronous nonblocking section](#async_usage) and would need to use the methods and techniques outlined in that section to achieve the goal of logical sub-spans without the restriction that they be perfectly nested.

<a name="sub_spans_for_downstream_calls"></a>
#### Using sub-spans to surround downstream calls

One common use case for sub-spans is to track downstream calls separately from the overall request (e.g. HTTP calls to another service, database calls, or any other call that crosses network or application boundaries). Start a sub-span immediately before a downstream call and complete it immediately after the downstream call returns. You can inspect the sub-span to see how long the downstream call took from this application's point of view. If you do this around all your downstream calls you can subtract the total time spent for all downstream calls from the time spent for the overall-request-span to determine time spent in this application vs. time spent waiting for downstream calls to finish. And if the downstream service also performs distributed tracing and has an overall-request-span for its service call then you can subtract the downstream service's request-span time from this application's sub-span time around the downstream call to determine how much time was lost to network lag or any other bottlenecks between the services.
 
<a name="propagating_traces"></a>
### Propagating Distributed Traces Across Network or Application Boundaries 

You can use the sub-span ability to create parent-child span relationships within an application, but the real utility of a distributed tracing system doesn't show itself until the trace crosses a network or application boundary (e.g. downstream calls to other services as part of a request).

The pattern for passing traces across network or application boundaries is that the calling service includes its "current span" information when calling the downstream service. The downstream service uses that information to generate its overall request span with the caller's span info as its parent span. This causes the downstream service's request span to contain the same Trace ID and sets up the correct parent-child relationship between the spans. 

For HTTP requests it is assumed that you will pass the caller's span information to the downstream system using the request headers defined in `TraceHeaders`. All headers defined in that class should be included in the downstream request, as well as any application specific user ID header if you want to take advantage of the optional user ID functionality of spans.

<a name="adjusting_behavior"></a>
### Adjusting Behavior and Execution Options

<a name="sampling"></a>
#### Sampling

Although this library is efficient and does not take much time for any single request, you may find it causes an unacceptable performance hit on high traffic and latency-sensitive applications (depending on the SLAs of the service) that process thousands of requests per second, usually due to the I/O cost of writing the log messages for every request to disk in high throughput scenarios. Google found that they could achieve the main goals of distributed tracing without negatively affecting performance in these cases by implementing trace sampling - i.e. only processing a certain percentage of requests rather than all requests.

If you find yourself in this situation you can adjust the sampling rate by calling `Tracer.setRootSpanSamplingStrategy(RootSpanSamplingStrategy)` and passing in a `RootSpanSamplingStrategy` that implements the sampling logic necessary for your use case. To achieve the maximum benefit you could implement an adaptive/dynamic sampling strategy that increases the sampling rate during low traffic periods and lessens the sampling rate during high traffic periods.

Many (most?) services will not notice or experience any performance hit for using this library to sample all requests (the default behavior), especially if you use asynchronous logging features with your SLF4J implementation. It's rare to find a service that needs to handle the combination of volume, throughput, and low-latency requirements of Google's services, therefore testing is recommended to verify that your service is suffering an unacceptable performance hit due to distributed tracing before adjusting sampling rates, and it's also recommended that you read the Google Dapper paper to understand the challenges Google faced and how they solved them with sampling.

<a name="span_lifecycle_events"></a>
#### Notification of span lifecycle events

You can be notified of span lifecycle events when spans are started, sampled, and completed (i.e. for metrics counting) by adding a listener via `Tracer.addSpanLifecycleListener(SpanLifecycleListener)`.
 
**NOTE:** It's important that any `SpanLifecycleListener` you add is extremely lightweight or you risk having the distributed tracing system become a major bottleneck for high throughput services. If any expensive work needs to be done in a `SpanLifecycleListener` then it should be done asynchronously on a dedicated thread or threadpool separate from the application worker threads.
 
<a name="logging_span_representation"></a> 
#### Changing serialized representation of Spans for the logs

Normally when a span is completed it is serialized to JSON and output to the logs. If you want spans to be output with a different representation such as key/value string, you can call `Tracer.setSpanLoggingRepresentation(SpanLoggingRepresentation)`, after which all subsequent spans that are logged will be serialized to the new representation.
 
<a name="async_usage"></a> 
## Usage in Reactive Asynchronous Nonblocking Scenarios 
 
Due to the thread-local nature of this library it is more effort to integrate with reactive (asynchronous non-blocking) frameworks like Netty or actor frameworks than with thread-per-request frameworks. But it is not terribly difficult and the benefit of having all your log messages automatically tagged with tracing information is worth the effort. The `Tracer` class provides the following methods to help integrate with reactive frameworks:

* `Tracer.registerWithThread(Deque)`
* `Tracer.unregisterFromThread()`
* `Tracer.getCurrentSpanStackCopy()`

See the javadocs on those methods for more detailed usage information, but the general pattern would be to call `registerWithThread(Deque)` with the request's span stack whenever a thread starts to do some chunk of work for that request, and call `unregisterFromThread()` when that chunk of work is done and the thread is about to be freed up to work on a different request. The span stack would need to follow the request no matter what thread was processing it, but assuming you can solve that problem in a reactive framework then the general pattern works well.

<a name="using_dtracing_for_errors"></a>
## Using Distributed Tracing to Help with Debugging Issues/Errors/Problems

If an application is setup to fully utilize the functionality of Wingtips then all log messages will include the TraceID for the distributed trace associated with that request. The TraceID should also be returned as a response header, so if you get the TraceID for a given request you can go log diving to find all log messages associated with that request and potentially discover where things went wrong. There are some potential drawbacks to using distributed tracing as a debugging tool:

* Not all requests are guaranteed to be traced. Depending on a service's throughput, SLA requirements, etc, it may need to implement trace sampling where only a percentage of requests are traced. Distributed tracing was primarily designed as a monitoring tool so sampling tradeoffs may need to be made to keep the overhead to an acceptable level. Google went as low as 0.01% sampling for some of their most high traffic and latency-sensitive services.
* Even if a given request is sampled it is not guaranteed that the logs will contain any messages that are helpful to the investigation.
* The trace IDs are pseudorandomly generated 64-bit long numbers. Therefore they are not guaranteed to be 100% unique. They are *probabilistically* unique but not guaranteed. The likelihood of collisions goes up quickly the more traces you're considering (see the [Birthday Paradox/Problem](http://en.wikipedia.org/wiki/Birthday_problem)), so you have to be careful to limit your searches to a reasonable amount of time and keep in mind that while collisions for a specific ID are very low it is technically possible.

That said, it can be extremely helpful in many cases for debugging or error investigation and is a benefit that should not be overlooked.
 
<a name="custom_annotations"></a>
## Custom Annotations

The Google Dapper paper describes how the Dapper tools allow them to associate arbitrary timestamped notes called "annotations" with any span. Wingtips does not currently support annotations, but the most important use case for annotations - knowing when a client sent a request vs when the server received it (and vice versa on the response) - is simulated in Wingtips by surrounding a client request with a sub-span and making sure the called service creates an overall request span for itself as well. This technique is described in the "[using sub-spans to surround downstream calls](#sub_spans_for_downstream_calls)" section. Even if Wingtips supported Dapper-style annotations this technique would likely still be required unless you instrumented your HTTP and/or RPC caller libraries at a very low level - a task that made sense for Google with their relatively homogenous ecosystem and developer resources, but not necessarily realistic for wider audiences.

Arbitrary application-specific annotations could be a useful addition however, so this feature may be added in the future. Until then you can output log messages tagged with tracing and timestamp information to approximate the functionality.

<a name="integrating_with_other_dtrace_tools"></a>
## Integrating With Other Distributed Tracing Tools
 
The logging behavior of Wingtips is somewhat useful without any additions - you can search or parse the distributed tracing output logs manually for any number of purposes directly on the server where the logs are output. There are limits to this approach however, especially as the number of servers in your ecosystem increases, and there are other distributed tracing tools out there for aggregating, searching, and visualizing distributed tracing information or logs in general that you might want to take advantage of. You should also be careful of doing too much on a production server - generally you want to do your searching and analysis offline on a different server both for convenience and to prevent disrupting production systems.

The typical way this goal is accomplished is to have a separate process on the server monitor the distributed tracing logs and pipe the information to an outside aggregator or collector for asynchronous/offline processing. You can use a general-purpose log aggregator that parses the application and tracing logs from all your services and exposes them via search interface, or you can use a distributed-tracing-specific tool like [Zipkin](https://github.com/openzipkin/zipkin/tree/master/zipkin-server), purpose-built for working with distributed trace spans, or any other number of possibilities.

Wingtips now contains some plug-and-play Zipkin support that makes sending spans to Zipkin servers easy. The [wingtips-zipkin](wingtips-zipkin/README.md) submodule's readme contains full details, but here's a quick example showing how you would configure Wingtips to send spans to a Zipkin server listening at `http://localhost:9411`:

```java
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener("some-service-name", 
                                          "some-local-component-name", 
                                          "http://localhost:9411")
);
```

Just execute that line as early in your application startup procedure as possible (ideally before any requests hit the service that would generate spans) and you'll see the Wingtips spans show up in the Zipkin UI.

<a name="license"></a>
## License

Wingtips is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

[travis]:https://travis-ci.org/Nike-Inc/wingtips
[travis img]:https://api.travis-ci.org/Nike-Inc/wingtips.svg?branch=master

[license]:LICENSE.txt
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg
