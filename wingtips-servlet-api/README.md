# Wingtips - wingtips-servlet-api

Wingtips is a distributed tracing solution for Java based on the [Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a Java Servlet environment. The features it provides are:

* **HttpSpanFactory** - Utility class that extracts span information from incoming `HttpServletRequest` requests.
* **RequestTracingFilter** - An abstract Servlet Filter class that handles 99% of the work for enabling a new span when a request comes in and completing it when the request finishes. This filter automatically uses `HttpSpanFactory` to extract parent span information from the incoming request headers for the new span if available. Sets the `X-B3-TraceId` response header to the Trace ID for each request. Supports Servlet 3.0+ asynchronous request processing. Concrete instances simply need to implement the `isAsyncDispatch(HttpServletRequest)` method and make sure the `user-id-header-keys-list` param is set if you expect any request headers that represent a user ID (if you don't have any user ID headers then this can be ignored).
* **RequestTracingFilterNoAsync** - A simple extension of `RequestTracingFilter` that assumes a non-asynchronous request environment.

Please make sure you have read the [base project README.md](../README.md). This readme assumes you understand the principles and usage instructions described there.

## NOTE

This module builds on the wingtips-core library. See that library's documentation for more detailed information on distributed tracing in general and this implementation in particular.

## Usage Example

The following example shows how you might setup the tracing Servlet Filter when the service expects one of two possible header keys that represent the user ID of the user making the call: `userid` or `altuserid`.

**Add the following to web.xml**

```
<filter>
    <filter-name>traceFilter</filter-name>
    <filter-class>com.nike.wingtips.servlet.RequestTracingFilterNoAsync</filter-class>
    <init-param>
        <param-name>user-id-header-keys-list</param-name>
        <param-value>userid,altuserid</param-value>
    </init-param>
</filter>

<filter-mapping>
    <filter-name>traceFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

If your service does not have any user ID headers you can remove the `<init-param>` element entirely or set the `<param-value>` to be empty.

That's it for incoming requests. This Filter will do the right thing and start a root span or child span for incoming requests (depending on whether or not the caller included tracing headers), add the trace ID to the response as a response header, and guarantees completion of the overall request span right before the response is sent.

### Propagating the Tracing Information to Downstream Systems
This Filter takes care of setting up the overall request span for incoming requests, but propagating the tracing information to downstream systems is still your responsibility. When you call another system you must grab the current span via `Tracer.getInstance().getCurrentSpan()` and put its field values into the downstream call's request headers using the constants in `TraceHeaders` as the header keys. For example:

```
Span currentSpan = Tracer.getInstance().getCurrentSpan();

otherSystemRequest.setHeader(TraceHeaders.TRACE_ID, currentSpan.getTraceId());
otherSystemRequest.setHeader(TraceHeaders.SPAN_ID, currentSpan.getSpanId());
otherSystemRequest.setHeader(TraceHeaders.PARENT_SPAN_ID, currentSpan.getParentSpanId());
otherSystemRequest.setHeader(TraceHeaders.SPAN_NAME, currentSpan.getSpanName());
otherSystemRequest.setHeader(TraceHeaders.TRACE_SAMPLED, currentSpan.isSampleable());
        
executeOtherSystemCall(otherSystemRequest);
```
