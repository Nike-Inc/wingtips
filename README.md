<img src="wingtips_logo.png" />

# Wingtips - Give Your Distributed Systems a Dapper Footprint

[ ![Download](https://api.bintray.com/packages/nike/maven/wingtips/images/download.svg) ](https://bintray.com/nike/maven/wingtips/_latestVersion)
[![][travis img]][travis]
[![Code Coverage](https://img.shields.io/codecov/c/github/Nike-Inc/wingtips/master.svg)](https://codecov.io/github/Nike-Inc/wingtips?branch=master)
[![][license img]][license]

Wingtips is a distributed tracing solution for Java 7 and greater based on the [Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

There are a few modules associated with this project:

* [wingtips-core](wingtips-core/README.md) - The core library providing the majority of the distributed tracing 
functionality.
* [wingtips-java8](wingtips-java8/README.md) - Provides several Java 8 helpers, particularly around helping tracing and 
MDC information to hop threads in asynchronous/non-blocking use cases.
* [wingtips-servlet-api](wingtips-servlet-api/README.md) - A plugin for Servlet based applications for integrating 
distributed tracing with a simple Servlet Filter. Supports Servlet 2.x and Servlet 3 (async request) environments. 
* [wingtips-zipkin2](wingtips-zipkin2/README.md) - A plugin providing easy [Zipkin](http://zipkin.io/) integration by 
converting Wingtips spans to Zipkin spans and sending them to a Zipkin server.
* [wingtips-spring](wingtips-spring/README.md) - A plugin to help with Wingtips distributed tracing in older
[Spring](https://spring.io/) environments (i.e. not Spring WebFlux). This is mostly for the older non-WebFlux 
HTTP clients - for Spring MVC serverside support, see 
[wingtips-servlet-api](wingtips-servlet-api), and for Spring WebFlux (both serverside and clientside) see
[wingtips-spring-webflux](wingtips-spring-webflux).
* [wingtips-spring-webflux](wingtips-spring-webflux/README.md) - A plugin to help with Wingtips distributed tracing in 
[Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux) 
environments.
* [wingtips-spring-boot](wingtips-spring-boot/README.md) - A plugin to help with Wingtips distributed tracing in 
[Spring Boot](https://spring.io/guides/gs/spring-boot/) environments using Spring Web MVC (Servlet-based). For 
Spring Boot 2 WebFlux environments see [wingtips-spring-boot2-webflux](wingtips-spring-boot2-webflux) instead.
* [wingtips-spring-boot2-webflux](wingtips-spring-boot2-webflux/README.md) - A plugin to help with Wingtips 
distributed tracing in Spring Boot 2 
[WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux) environments
(not Servlet-based Web MVC environments - for that see [wingtips-spring-boot](wingtips-spring-boot)). 
* [wingtips-zipkin2-spring-boot](wingtips-zipkin2-spring-boot/README.md) - A plugin to help with Wingtips distributed
tracing in [Spring Boot](https://spring.io/guides/gs/spring-boot/) environments that also utilize 
[Zipkin](http://zipkin.io/).  
* [wingtips-apache-http-client](wingtips-apache-http-client/README.md) - A plugin to help with Wingtips distributed
tracing when using Apache's `HttpClient`.
* [wingtips-jersey2](wingtips-jersey2/README.md) - A plugin for Jersey 2 based applications. This is intended to be
used in conjunction with the `RequestTracingFilter` from [wingtips-servlet-api](wingtips-servlet-api). 

If you prefer hands-on exploration rather than readmes, the [sample applications](#samples) provide concrete examples 
of using Wingtips that are simple, compact, and straightforward.

## Table of Contents

* [Overview](#overview)
    * [What is a Distributed Trace Made Of?](#trace_and_span_anatomy) 
* [Quickstart and Usage](#quickstart)
    * [Generic Application Pseudo-Code](#generic_pseudo_code)
    * [Generic Application Pseudo-Code Explanation](#generic_pseudo_code_info)
        * [Is your application running in a Servlet-based framework?](#servlet_filter_info)
    * [Simplified Span Management using Java `try-with-resources` Statements](#try_with_resources_info)  
    * [Output and Logging](#output_and_logging)
        * [Automatically attaching trace information to all log messages](#mdc_info)
    * [Nested Sub-Spans and the Span Stack](#sub_spans)
        * [Using sub-spans to surround downstream calls](#sub_spans_for_downstream_calls)
    * [Propagating Distributed Traces Across Network or Application Boundaries](#propagating_traces)
    * [Adjusting Behavior and Execution Options](#adjusting_behavior)
        * [Sampling](#sampling)
        * [Notification of span lifecycle events](#span_lifecycle_events)
        * [Changing serialized representation of Spans for the logs](#logging_span_representation)
    * [Span Tags](#span_tags)
        * [HTTP Span Tag and Naming Strategies and Adapters](#tag_strategies_and_adapters) 
        * [Default HTTP Tags](#default_http_tags) 
    * [Custom Timestamped Span Annotations](#custom_annotations)
* [Usage in Reactive Asynchronous Nonblocking Scenarios](#async_usage)
* [Using Distributed Tracing to Help with Debugging Issues/Errors/Problems](#using_dtracing_for_errors)
* [Integrating With Other Distributed Tracing Tools](#integrating_with_other_dtrace_tools)
* [Sample Applications](#samples)
* [License](#license)

<a name="overview"></a> 
## Overview

Distributed tracing is a mechanism to track requests through a network of distributed systems in order to create transparency and shed light on the sometimes complex interactions and behavior of those systems. For example in a cloud-based microservice architecture a single request can touch dozens or hundreds of servers as it spreads out in a tree-like fashion. Without distributed tracing it would be very difficult to identify which part(s) of such a complex system were causing performance issues - either in general or for that request in particular. 

Distributed tracing provides the capability for near-realtime monitoring *or* historical analysis of interactions between servers given the necessary tools to collect and interpret the traces. It allows you to easily see where applications are spending their time - e.g. is it mostly in-application work, or is it mostly waiting for downstream network calls?

Distributed tracing can also be used in some cases for error debugging and problem investigations with some caveats. See [the relevant section](#using_dtracing_for_errors) for more information on this topic.

<a name="trace_and_span_anatomy"></a>
### What is a Distributed Trace Made Of?

* Every distributed trace contains a TraceID that represents the entire request across all servers/microservices it touches. These TraceIDs are generated as probabilistically unique 64-bit integers (longs). In practice these IDs are passed around as unsigned longs in lowercase hexadecimal string format, but conceptually they are just random 64-bit integers.
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


``` java
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

**NOTE:** Given the thread-local nature of this library you'll want to make sure the span completion call is in a finally block or otherwise guaranteed to be called no matter what (even if the request fails with an error) to prevent problems when subsequent requests are processed on the same thread. The `Tracer` class does its best to recover from incorrect thread usage scenarios and log information about what happened but the best solution is to prevent the problems from occurring in the first place. See the section below on `try-with-resources` for some tips on foolproof ways to safely complete your spans.

<a name="servlet_filter_info"></a>  
#### Is your application running in a Servlet-based framework?

If your application is running in a Servlet environment (e.g. Spring Boot w/ Spring MVC, raw Spring MVC, Jersey, 
raw Servlets, etc) then 
this entire lifecycle can be handled by a Servlet `Filter`. We've created one for you that's ready to drop in and go - 
see the [wingtips-servlet-api](wingtips-servlet-api/README.md) Wingtips plugin module library for details. That plugin 
module is also a good resource to see how the code for a production-ready implementation of this library might look.

<a name="try_with_resources_info"></a>
### Simplified Span Management using Java `try-with-resources` Statements

`Span`s support Java `try-with-resources` statements to help guarantee proper usage in blocking/non-asynchronous scenarios 
(for asynchronous scenarios please refer to the [asynchronous usage section](#async_usage) of this readme). As 
previously mentioned, `Span`s that are not properly completed can lead to incorrect distributed tracing information 
showing up, and the `try-with-resources` statements guarantee that spans are completed appropriately. Here are some 
examples *(note: there are some [important tradeoffs](#try_with_resources_warning) you should consider before using this 
feature)*:

#### Overall request span using `try-with-resources`

``` java
try(Span requestSpan = Tracer.getInstance().startRequestWith*(...)) {
    // Traced blocking code for overall request (not asynchronous) goes here ...
}
// No finally block needed to properly complete the overall request span
```
   
#### Subspan using `try-with-resources`

``` java
try (Span subspan = Tracer.getInstance().startSubSpan(...)) {
    // Traced blocking code for subspan (not asynchronous) goes here ...
}
// No finally block needed to properly complete the subspan
```

<a name="try_with_resources_warning"></a> 
#### Warning about error handling when using `try-with-resources` to autoclose spans

The `try-with-resources` feature to auto-close spans as described above can sound very tempting due to its convenience,
but it comes with an important and easy-to-miss tradeoff: the span will be closed *before* any `catch` or `finally` 
blocks get a chance to execute. So if you need to catch any exceptions and log information about them (for example),
then you do *not* want to use the `try-with-resources` shortcut because that logging will not be tagged with the span 
info of the span it logically falls under, and if you try to retrieve `Tracer.getInstance().getCurrentSpan()` then 
you'll either get the parent span if one exists or null if there was no parent span. This can be confusing and seem
counter-intuitive, but it's the way `try-with-resources` works and is the price we pay for (ab)using it for convenience
in a use case it wasn't originally intended for (`Span`s are not "resources" in the traditional sense).

Because of these drawbacks, and because it's easy to forget about this caveat and add a `catch` block at some future 
date and not get the behavior you expect, it's not recommended that you use this feature as common practice - or if you 
do make sure you call it out with some in-line comments for the inevitable future when someone tries to add a `catch` 
block. Instead it's recommended that you complete the span in a `finally` block manually as described in the 
[Generic Application Pseudo-Code](#generic_pseudo_code) section. It's a few extra lines of code, but it's
simple and prevents confusing unexpected behavior.

Thanks to [Adrian Cole](https://github.com/adriancole) for pointing out this danger.   
 
<a name="output_and_logging"></a>  
### Output and Logging

When `Tracer` completes a span it will log it to a SLF4J logger named `VALID_WINGTIPS_SPANS` so you can segregate your span information into a separate file if desired. The following is an example of log output for a valid span (in this case it is a root span because it does not have a parent span ID):

```
14:02:26.029 [main] INFO  VALID_WINGTIPS_SPANS - [DISTRIBUTED_TRACING] {"traceId":"776d455c76fded18","parentSpanId":"null","spanId":"030ab15d0bb503a8","spanName":"somespan","sampleable":"true","userId":"null","startTimeEpochMicros":"1445720545485958","durationNanos":"543516000"}
```

 If an invalid span is detected due to incorrect usage of `Tracer` then the invalid span will be logged to a SLF4J logger named `INVALID_WINGTIPS_SPANS`. These specially-named loggers will not be used for any other purpose.

<a name="mdc_info"></a> 
#### Automatically attaching trace information to all log messages

In addition to the logs this class outputs for completed spans it puts the trace ID for the "current" span into the 
SLF4J [MDC](http://www.slf4j.org/manual.html#mdc) so that all your logs can be tagged with the current span's trace ID. 
To utilize this you would need to add `%X{traceId}` to your log pattern (*NOTE: this only works with SLF4J frameworks 
that support MDC, e.g. Logback and Log4j*). This causes *all* log messages, including ones that come from third party 
libraries and have no knowledge of distributed tracing, to be output with the current span's tracing information.

Here is an example [Logback pattern](http://logback.qos.ch/manual/layouts.html) utilizing the tracing MDC info:

```
traceId=%X{traceId} %date{HH:mm:ss.SSS} %-5level [%thread] %logger - %m%n
```

And here is what a log message output would look like when using this pattern:

```
traceId=520819c556734c0c 14:43:53.483 INFO  [main] com.foo.Bar - important log message
```

***This is one of the primary features and benefits of Wingtips - if you utilize this MDC feature then you'll be able 
to trivially collect all log messages related to a specific request across all services it touched even if some of 
those messages came from third party libraries.***

A [Log4j pattern](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html) would look 
similar - in particular `%X{traceId}` to access the trace ID in the MDC is identical.

Note: You can adjust which span fields are included in the MDC behavior by calling 
`Tracer.setSpanFieldsForLoggerMdc(...)`. It's recommended that you always include trace ID, but if you want to also 
include span ID, parent span ID, or even the full span JSON in the MDC (not recommended), you can.

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

#### Propagating tracing info for HTTP client requests

**TL;DR**

Wingtips uses the [Zipkin/B3 specification](http://zipkin.io/pages/instrumenting.html) for HTTP tracing propagation 
by setting specially-named request headers to the values of the caller's `Span`. There is a handy 
`HttpRequestTracingUtils.propagateTracingHeaders(...)` helper method that performs this work so that it conforms to the 
B3 spec - all you need to do is wrap your request or headers object in an implementation of the 
`HttpObjectForPropagation` interface and pass it to `HttpRequestTracingUtils.propagateTracingHeaders(...)` along with 
your current span. The helper method will set the B3 headers on your request to the appropriate values based on the 
`Span` you pass in.  

**Details**

If you don't want to use the helper method described above then here are the technical details on how to propagate
tracing info on a HTTP client request yourself:

For HTTP requests it is assumed that you will pass the caller's span information to the downstream system using the request headers defined in `TraceHeaders`. Most headers defined in that class should be included in the downstream request (see below), as well as any application specific user ID header if you want to take advantage of the optional user ID functionality of spans. Note that to be [properly B3 compatible](http://zipkin.io/pages/instrumenting.html) (and therefore compatible with Zipkin systems) you should send the `X-B3-Sampled` header value as `"0"` for `false` and `"1"` for `true`. All the other header values should be correct if you send the appropriate `Span` property as-is, e.g. send `Span.getTraceId()` for the `X-B3-TraceId` header as it should already be in the correct B3 format.

**Propagation requirements:**
 
* Trace ID: \[required]
* Span ID: \[required]
* Sampleable: \[optional but recommended]
* Parent Span ID: \[optional when non-null, don't propagate when null]
* Span Name: \[highly optional] - Propagating span name is optional, and you may wish to intentionally include or 
exclude it depending on whether you want downstream systems to have access to that info. When calling downstream
services you control it may be good to include it for extra debugging info, and for downstream services outside your 
control you may wish to exclude it to prevent unintentional information leakage.

#### Tooling to help with tracing propagation

The following Wingtips modules have helpers to simplify tracing propagation when using their respective technologies:

* [wingtips-apache-http-client](wingtips-apache-http-client)
* [wingtips-spring](wingtips-spring) (for the older `RestTemplate` and `AsyncRestTemplate` HTTP clients)
* [wingtips-spring-webflux](wingtips-spring-webflux) (for the newer `WebClient` reactive HTTP client)

<a name="adjusting_behavior"></a>
### Adjusting Behavior and Execution Options

<a name="sampling"></a>
#### Sampling

Although this library is efficient and does not take much time for any single request, you may find it causes an unacceptable performance hit on high traffic and latency-sensitive applications (depending on the SLAs of the service) that process thousands of requests per second, usually due to the I/O cost of writing the log messages for every request to disk in high throughput scenarios. Google found that they could achieve the main goals of distributed tracing without negatively affecting performance in these cases by implementing trace sampling - i.e. only processing a certain percentage of requests rather than all requests.

If you find yourself in this situation you can adjust the sampling rate by calling `Tracer.getInstance().setRootSpanSamplingStrategy(RootSpanSamplingStrategy)` and passing in a `RootSpanSamplingStrategy` that implements the sampling logic necessary for your use case. To achieve the maximum benefit you could implement an adaptive/dynamic sampling strategy that increases the sampling rate during low traffic periods and lessens the sampling rate during high traffic periods.

Many (most?) services will not notice or experience any performance hit for using this library to sample all requests (the default behavior), especially if you use asynchronous logging features with your SLF4J implementation. It's rare to find a service that needs to handle the combination of volume, throughput, and low-latency requirements of Google's services, therefore testing is recommended to verify that your service is suffering an unacceptable performance hit due to distributed tracing before adjusting sampling rates, and it's also recommended that you read the Google Dapper paper to understand the challenges Google faced and how they solved them with sampling.

<a name="span_lifecycle_events"></a>
#### Notification of span lifecycle events

You can be notified of span lifecycle events when spans are started, sampled, and completed (i.e. for metrics counting) by adding a listener via `Tracer.addSpanLifecycleListener(SpanLifecycleListener)`.
 
**NOTE:** It's important that any `SpanLifecycleListener` you add is extremely lightweight or you risk having the distributed tracing system become a major bottleneck for high throughput services. If any expensive work needs to be done in a `SpanLifecycleListener` then it should be done asynchronously on a dedicated thread or threadpool separate from the application worker threads.
 
<a name="logging_span_representation"></a> 
#### Changing serialized representation of Spans for the logs

Normally when a span is completed it is serialized to JSON and output to the logs. If you want spans to be output with a different representation such as key/value string, you can call `Tracer.setSpanLoggingRepresentation(SpanLoggingRepresentation)`, after which all subsequent spans that are logged will be serialized to the new representation.

<a name="span_tags"></a>
### Span Tags

Tags allow for key-value pairs to be associated with a span as metadata, often useful for filtering or grouping trace 
information, or integrating with a visualization/analytics system that expects certain tags. For example, say you've 
instrumented retry logic. It may be desirable to know how many retries were attempted. Or you may want to tag your 
spans based on an attribute of the request, like user type or an authenticated flag. 

```
Tracer.getInstance().getCurrentSpan().putTag("UserType", user.getType());
```

Both keys and values are stored as strings. Calling `Span.putTag(...)` will replace any existing value for the key, or 
add the new key value pair if one with that key doesn't already exist. 

NOTE: If you're wanting to record the time when some event occurred, you should probably use a 
[timestamped annotation](#custom_annotations) instead of a tag. This can provide extra benefits, especially when
using visualization or analytics systems that parse timestamped annotations and do interesting things with them. 

<a name="tag_strategies_and_adapters"></a>
#### HTTP Span Tag and Naming Strategies and Adapters

HTTP Span Tag and Naming Strategies are a set of classes that apply consistent tagging and span naming *automatically*
to every span based on data extracted from the HTTP request and response. There are a few default
[HttpTagAndSpanNamingStrategy](wingtips-core/src/main/java/com/nike/wingtips/tags/HttpTagAndSpanNamingStrategy.java) 
implementations you can take advantage of:

* [ZipkinHttpTagStrategy](wingtips-core/src/main/java/com/nike/wingtips/tags/ZipkinHttpTagStrategy.java) - Based on 
[this Zipkin documentation about Span data policy in HTTP instrumentation](https://github.com/openzipkin/brave/tree/master/instrumentation/http#span-data-policy),
and makes sure your spans are named and tagged such that a Zipkin server can work with them the way it expects. You can 
reference Wingtips' `KnownZipkinTags` class to access constants for these without having to pull in Zipkin dependencies 
for any additional tags you wish to implement. 
* [OpenTracingHttpTagStrategy](wingtips-core/src/main/java/com/nike/wingtips/tags/OpenTracingHttpTagStrategy.java) -
This is currently effectively a subset of the `ZipkinHttpTagStrategy` (same tag names and values, just fewer of them).
It may diverge in the future. This uses constants from 
[the OpenTracing Tags class](https://github.com/opentracing/opentracing-java/blob/master/opentracing-api/src/main/java/io/opentracing/tag/Tags.java).
You can reference Wingtips' `KnownOpenTracingTags` class to access these constants without having to pull in 
OpenTracing dependencies. 
* [NoOpHttpTagStrategy](wingtips-core/src/main/java/com/nike/wingtips/tags/NoOpHttpTagStrategy.java) - Does nothing
when called. Use this if you want to turn off all span tagging.

Note that the `ZipkinHttpTagStrategy` and `OpenTracingHttpTagStrategy` implementations instrument a subset of the total 
known tags - if there are other tags that you need, you are free to add them.

These tag and naming strategies are reusable for both server-side and client-side span tagging. They don't care about 
what the request and response objects are, but they do need information from the request and response. This gap is
bridged with 
[HttpTagAndSpanNamingAdapter](wingtips-core/src/main/java/com/nike/wingtips/tags/HttpTagAndSpanNamingAdapter.java),
which is implemented for each individual request/response class type for a given HTTP server or client library. The
tag and naming adapter is given to the tag and naming strategy in order to extract the necessary data from the 
request and response.  

We currently have the following default adapters you can use:

* [ServletRequestTagAdapter](wingtips-servlet-api/src/main/java/com/nike/wingtips/servlet/tag/ServletRequestTagAdapter.java) -
Knows how to extract data from Servlet `HttpServletRequest` and `HttpServletResponse` objects.
* [SpringWebfluxServerRequestTagAdapter](wingtips-spring-webflux/src/main/java/com/nike/wingtips/spring/webflux/server/SpringWebfluxServerRequestTagAdapter.java) -
Knows how to extract data from Spring WebFlux `ServerWebExchange` and `ServerHttpResponse` objects (serverside).
* [SpringWebfluxClientRequestTagAdapter](wingtips-spring-webflux/src/main/java/com/nike/wingtips/spring/webflux/client/SpringWebfluxClientRequestTagAdapter.java) -
Knows how to extract data from Spring WebFlux `ClientRequest` `ClientResponse` objects (clientside).
* [SpringHttpClientTagAdapter](wingtips-spring/src/main/java/com/nike/wingtips/spring/interceptor/tag/SpringHttpClientTagAdapter.java) -
Knows how to extract data from Spring `HttpRequest` and `ClientHttpResponse` objects.
* [ApacheHttpClientTagAdapter](wingtips-apache-http-client/src/main/java/com/nike/wingtips/apache/httpclient/tag/ApacheHttpClientTagAdapter.java) -
Knows how to extract data from Apache HttpClient `HttpRequest` and `HttpResponse`.

Creating new `HttpTagAndSpanNamingStrategy` and/or `HttpTagAndSpanNamingAdapter` classes or extending existing ones
is not difficult - see the javadocs on those classes.

The Wingtips instrumentation for various HTTP server and client frameworks/libraries will all default to using 
`ZipkinHttpTagStrategy` and the appropriate adapter for the framework/library. If you want something else, they all 
have ways to configure them to use different tag and naming strategies and/or custom adapters. See the javadocs on 
those classes for details on how to customize the strategy and/or adapter that gets used:

* [RequestTracingFilter](wingtips-servlet-api/src/main/java/com/nike/wingtips/servlet/RequestTracingFilter.java) - For
Servlet-based containers or frameworks, including (but not limited to) Spring/Springboot, Jersey 1, Jersey 2, etc.
* [WingtipsSpringWebfluxWebFilter](wingtips-spring-webflux/src/main/java/com/nike/wingtips/spring/webflux/server/WingtipsSpringWebfluxWebFilter.java) -
For Spring WebFlux servers.
* [WingtipsSpringWebfluxExchangeFilterFunction](wingtips-spring-webflux/src/main/java/com/nike/wingtips/spring/webflux/client/WingtipsSpringWebfluxExchangeFilterFunction.java) -
For Spring WebFlux `WebClient` HTTP clients.
* [WingtipsClientHttpRequestInterceptor](wingtips-spring/src/main/java/com/nike/wingtips/spring/interceptor/WingtipsClientHttpRequestInterceptor.java)
and [WingtipsAsyncClientHttpRequestInterceptor](wingtips-spring/src/main/java/com/nike/wingtips/spring/interceptor/WingtipsAsyncClientHttpRequestInterceptor.java) -
For Spring `RestTemplate` and `AsyncRestTemplate` HTTP clients.
* [WingtipsHttpClientBuilder](wingtips-apache-http-client/src/main/java/com/nike/wingtips/apache/httpclient/WingtipsHttpClientBuilder.java) 
and [WingtipsApacheHttpClientInterceptor](wingtips-apache-http-client/src/main/java/com/nike/wingtips/apache/httpclient/WingtipsApacheHttpClientInterceptor.java) -
For Apache `HttpClient`.
 
<a name="default_http_tags"></a> 
#### Default HTTP Tags 

The default tag strategy is `ZipkinHttpTagStrategy`. Here's a quick rundown of the tags you get (more info on these 
tags can be found in `KnownZipkinTags`):

|  Tag               | Description                                             | Example value |
| :----------------- | :------------------------------------------------------ | :------------ |
| `http.method`      | The HTTP request method used.                           | `GET` |
| `http.path`        | The HTTP path of the request (path only, not full URL). | `/some/path`  |
| `http.url`         | The full URL of the request, including scheme, host, path, and query string.  | `http://some.host/some/path?fooQueryParam=bar` |
| `http.route`       | The low-cardinality "template" version of the path. i.e. `/some/path/{id}` instead of `/some/path/12345`. This tag will only show up if the library or framework provides a mechanism to determine the path template. If available, it will also be used to help name the span. | `/some/path/{id}` |
| `http.status_code` | The response HTTP status code.                          | `200`         |
| `error`            | Only exists if the request is considered an error. If an exception occurred then its message or classname will be used as the tag value. If no exception occurred then the request can still be considered an error if `HttpTagAndSpanNamingAdapter.getErrorResponseTagValue(...)` returns a non-empty value. That value will be used as the tag value. Most HTTP client adapters consider 4xx or 5xx response codes to indicate an error, while server adapters usually only consider 5xx to be an error. In either case adapters often use the response HTTP status code as the error tag value. | `An error occurred while doing foo`, `FooException`, or `500` |

<a name="custom_annotations"></a>
### Custom Timestamped Span Annotations

In addition to [tags](#span_tags), Wingtips Spans support timestamped annotations. Timestamped annotations are 
arbitrary notes with a timestamp attached, usually for the purpose of recording when certain events occurred. These
will be output along with the usual Wingtips Span data and tags.

The usual mechanism for recording a timestamped annotation is to make a 
`Span.addTimestampedAnnotationForCurrentTime(String)` method call to record an annotation for the current time when
the event occurs, e.g.:

``` java
requestSpan.addTimestampedAnnotationForCurrentTime("retry.initiated");
``` 

The timestamps for annotations created in this fashion have microsecond precision *relative to the Span's start 
timestamp* (which usually only has millisecond precision due to JVM System clock limitations). 

If you need to record an annotation for a different timestamp than when you're adding the annotation, you can create
a `TimestampedAnnotation` manually, and then call `Span.addTimestampedAnnotation(TimestampedAnnotation)`.

NOTE: The most important and common use case for timestamped events is knowing when a client sent a request vs when 
the server received it (and vice versa on the response). Although you could use the custom timestamped annotations
feature described above to do this, it's better to surround a client request with a sub-span and make sure the 
called service creates an overall request span for itself as well. This technique is described in the 
"[using sub-spans to surround downstream calls](#sub_spans_for_downstream_calls)" section. Using subspans for 
request/response timing instead of custom annotations is more reliable (less chance of forgetting to add the 
important annotations), prevents polluting a server span with client span tags or annotations (and vice versa), 
prevents incompatibilities with various tracing visualization and analytics systems that might not use the 
annotation names you picked, and is conceptually more consistent (server calls and client calls should be separate 
spans).
 
<a name="async_usage"></a> 
## Usage in Reactive Asynchronous Nonblocking Scenarios 
 
Due to the thread-local nature of this library it is more effort to integrate with reactive (asynchronous non-blocking) frameworks like Netty or actor frameworks than with thread-per-request frameworks. But it is not terribly difficult and the benefit of having all your log messages automatically tagged with tracing information is worth the effort. The `Tracer` class provides the following methods to help integrate with reactive frameworks:

* `Tracer.registerWithThread(Deque)`
* `Tracer.unregisterFromThread()`
* `Tracer.getCurrentSpanStackCopy()`
* `Tracer.getCurrentTracingStateCopy()` (not strictly necessary, but helpful for convenience)

See the javadocs on those methods for more detailed usage information, but the general pattern would be to call `registerWithThread(Deque)` with the request's span stack whenever a thread starts to do some chunk of work for that request, and call `unregisterFromThread()` when that chunk of work is done and the thread is about to be freed up to work on a different request. The span stack would need to follow the request no matter what thread was processing it, but assuming you can solve that problem in a reactive framework then the general pattern works well.

**NOTE:** The [wingtips-java8](wingtips-java8/README.md) module contains numerous helpers to make dealing with async scenarios easy. See that module's readme and the javadocs for `AsyncWingtipsHelper` for full details, however here's some code examples for a few common use cases:

* An example of making the current thread's tracing and MDC info hop to a thread executed by an `Executor`:

``` java
import static com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing.withTracing;

// ...

// Just an example - please use an appropriate Executor for your use case.
Executor executor = Executors.newSingleThreadExecutor(); 

executor.execute(withTracing(() -> {
    // Code that needs tracing/MDC wrapping goes here
}));
```

* Or use `ExecutorServiceWithTracing` so you don't forget to wrap your `Runnable`s or `Callable`s (WARNING: be careful
if you have to spin off work that *shouldn't* automatically inherit the calling thread's tracing state, e.g. long-lived
background threads - in those cases you should *not* use an `ExecutorServiceWithTracing` to spin off that work):

``` java
import static com.nike.wingtips.util.asynchelperwrapper.ExecutorServiceWithTracing.withTracing;

// ...

// Just an example - please use an appropriate Executor for your use case.
Executor executor = withTracing(Executors.newSingleThreadExecutor());

executor.execute(() -> {
    // Code that needs tracing/MDC wrapping goes here
});
```

* A similar example using `CompletableFuture`:

``` java
import static com.nike.wingtips.util.asynchelperwrapper.SupplierWithTracing.withTracing;

// ...

CompletableFuture.supplyAsync(withTracing(() -> {
    // Supplier code that needs tracing/MDC wrapping goes here.
    return foo;
}));
```

* This example shows how you might accomplish tasks in an environment where the tracing information is attached
to some request context, and you need to temporarily attach the tracing info in order to do something (e.g. log some
messages with tracing info automatically added using MDC):

``` java
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;

// ...

TracingState tracingInfo = requestContext.getTracingInfo();
runnableWithTracing(
    () -> {
        // Code that needs tracing/MDC wrapping goes here
    },
    tracingInfo
).run();
```

* If you want to use the link and unlink methods manually to wrap some chunk of code, the general procedure looks
like this:

``` java
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

// ...

TracingState originalThreadInfo = null;
try {
    originalThreadInfo = linkTracingToCurrentThread(...);
    // Code that needs tracing/MDC wrapping goes here
}
finally {
    unlinkTracingFromCurrentThread(originalThreadInfo);
}
```

**ALSO NOTE:** `wingtips-core` does contain a small subset of the async helper functionality described above for the 
bits that are Java 7 compatible, such as `Runnable`, `Callable`, and `ExecutorService`. See `AsyncWingtipsHelperJava7` 
if you're in a Java 7 environment and cannot upgrade to Java 8. If you're in Java 8, please use `AsyncWingtipsHelper` 
or `AsyncWingtipsHelperStatic` rather than `AsyncWingtipsHelperJava7`.

<a name="using_dtracing_for_errors"></a>
## Using Distributed Tracing to Help with Debugging Issues/Errors/Problems

If an application is setup to fully utilize the functionality of Wingtips then all log messages will include the 
TraceID for the distributed trace associated with that request. The TraceID should also be returned as a response 
header, so if you get the TraceID for a given request you can go log diving to find all log messages associated with 
that request and potentially discover where things went wrong. There are some potential drawbacks to using distributed 
tracing as a debugging tool:

* Not all requests are guaranteed to be traced. Depending on a service's throughput, SLA requirements, etc, it may 
need to implement trace sampling where only a percentage of requests are traced. Distributed tracing was primarily 
designed as a monitoring tool so sampling tradeoffs may need to be made to keep the overhead to an acceptable level. 
Google went as low as 0.01% sampling for some of their most high traffic and latency-sensitive services. 
* Even if a given request is sampled it is not guaranteed that the logs will contain any messages that are helpful to 
the investigation. 
* The trace IDs are pseudorandomly generated 64-bit long numbers. Therefore they are not guaranteed to be 100% unique. 
They are *probabilistically* unique but not guaranteed. The likelihood of collisions goes up quickly the more traces 
you're considering (see the [Birthday Paradox/Problem](http://en.wikipedia.org/wiki/Birthday_problem)), so you have 
to be careful to limit your searches to a reasonable amount of time and keep in mind that while collisions for a 
specific ID are very low it is technically possible.

That said, it can be extremely helpful in many cases for debugging or error investigation and is a benefit that 
should not be overlooked.
 
<a name="integrating_with_other_dtrace_tools"></a>
## Integrating With Other Distributed Tracing Tools
 
The logging behavior of Wingtips is somewhat useful without any additions - you can search or parse the distributed tracing output logs manually for any number of purposes directly on the server where the logs are output. There are limits to this approach however, especially as the number of servers in your ecosystem increases, and there are other distributed tracing tools out there for aggregating, searching, and visualizing distributed tracing information or logs in general that you might want to take advantage of. You should also be careful of doing too much on a production server - generally you want to do your searching and analysis offline on a different server both for convenience and to prevent disrupting production systems.

The typical way this goal is accomplished is to have a separate process on the server monitor the distributed tracing logs and pipe the information to an outside aggregator or collector for asynchronous/offline processing. You can use a general-purpose log aggregator that parses the application and tracing logs from all your services and exposes them via search interface, or you can use a distributed-tracing-specific tool like [Zipkin](https://github.com/openzipkin/zipkin/tree/master/zipkin-server), purpose-built for working with distributed trace spans, or any other number of possibilities.

Wingtips now contains some plug-and-play Zipkin support that makes sending spans to Zipkin servers easy. The [wingtips-zipkin2](wingtips-zipkin2/README.md) submodule's readme contains full details, but here's a quick example showing how you would configure Wingtips to send spans to a Zipkin server listening at `http://localhost:9411`:

``` java
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener("some-service-name", 
                                          "http://localhost:9411")
);
```

Just execute that line as early in your application startup procedure as possible (ideally before any requests hit the service that would generate spans) and you'll see the Wingtips spans show up in the Zipkin UI.

<a name="samples"></a>
### Sample Applications
   
The following sample applications show how Wingtips can be used in various frameworks and use cases. The 
`VerifySampleEndpointsComponentTest` component tests in the sample apps exercise important parts of Wingtips 
functionality - you can learn a lot by running those component tests, seeing what the sample apps return and the log 
messages they output, and exploring the associated endpoints in the sample apps to see how it all fits together. 

See the sample app readmes for further information on building and running the sample apps as well as things to try:
   
* [samples/sample-jersey1](samples/sample-jersey1/)
* [samples/sample-jersey2](samples/sample-jersey2/)
* [samples/sample-spring-web-mvc](samples/sample-spring-web-mvc/)
* [samples/sample-spring-boot](samples/sample-spring-boot/)
* [samples/sample-spring-boot2-webflux](samples/sample-spring-boot2-webflux/)

<a name="license"></a>
## License

Wingtips is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

[travis]:https://travis-ci.org/Nike-Inc/wingtips
[travis img]:https://api.travis-ci.org/Nike-Inc/wingtips.svg?branch=master

[license]:LICENSE.txt
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg
