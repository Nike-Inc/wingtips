# Wingtips - wingtips-old-servlet-api

Wingtips is a distributed tracing solution for Java based on the [Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a Java **Servlet 2.x API** environment (for Servlet 3+ environments please refer to the [wingtips-servlet-api](../wingtips-servlet-api) module). The features it provides are:

* **HttpSpanFactory** - Utility class that extracts span information from incoming `HttpServletRequest` requests.
* **RequestTracingFilterOldServlet** - A Servlet Filter that handles all of the work for enabling a new span when a request comes in and completing it when the request finishes. This filter automatically uses `HttpSpanFactory` to extract parent span information from the incoming request headers for the new span if available. Sets the `X-B3-TraceId` response header to the Trace ID for each request. You can set the `user-id-header-keys-list` servlet filter param if you expect any request headers that represent a user ID (if you don't have any user ID headers then this can be ignored).

Please make sure you have read the [base project README.md](../README.md). This readme assumes you understand the principles and usage instructions described there.

## Usage Example

The following example shows how you might setup the tracing Servlet Filter when the service expects one of two possible header keys that represent the user ID of the user making the call: `userid` or `altuserid`.

**Add the following to web.xml**

``` xml
<filter>
    <filter-name>traceFilter</filter-name>
    <filter-class>com.nike.wingtips.servlet.RequestTracingFilterOldServlet</filter-class>
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

**Embedded environments**

For embedded Servlet container environments where you may not be using a `web.xml` file to setup Servlet components 
you'll need to register `RequestTracingFilterOldServlet` in whatever way your Servlet container allows or requires you 
to register Servlet Filters. For example the `Main` classes in the `samples/sample-*` sample projects show how to 
register `RequestTracingFilter` with embedded Jetty.  

### Propagating the Tracing Information to Downstream Systems

This Filter takes care of setting up the overall request span for incoming requests, but propagating the tracing information to downstream systems is still your responsibility. When you call another system you must grab the current span via `Tracer.getInstance().getCurrentSpan()` and put its field values into the downstream call's request headers using the constants in `TraceHeaders` as the header keys. For example:

``` java
Span currentSpan = Tracer.getInstance().getCurrentSpan();

otherSystemRequest.setHeader(TraceHeaders.TRACE_ID, currentSpan.getTraceId());
otherSystemRequest.setHeader(TraceHeaders.SPAN_ID, currentSpan.getSpanId());
otherSystemRequest.setHeader(TraceHeaders.TRACE_SAMPLED, (currentSpan.isSampleable()) ? "1" : "0");
if (currentSpan.getParentSpanId() != null)
    otherSystemRequest.setHeader(TraceHeaders.PARENT_SPAN_ID, currentSpan.getParentSpanId());
if (shouldSendSpanName)
    otherSystemRequest.setHeader(TraceHeaders.SPAN_NAME, currentSpan.getSpanName());
        
executeOtherSystemCall(otherSystemRequest);
```

Propagating trace ID and span ID is required. Propagating parent span ID (if non-null) and sampleable value is optional
but recommended.

The `TraceHeaders.SPAN_NAME` header propagation is optional, and you may wish to intentionally include or exclude it 
depending on whether you want downstream systems to have access to that info. For services you control it may be good
to include it for extra debugging info, and for services outside your control you may wish to exclude it to prevent
unintentional information leakage.

See the [base project readme's section on propagation](../README.md#propagating_traces) for further details on 
propagating tracing information. You may also want to consider 
[wrapping downstream calls in a subspan](../README.md#sub_spans_for_downstream_calls).

## NOTE - Servlet API 2.3 or later dependency required at runtime

This `wingtips-old-servlet-api` module has a minimum Servlet 2.3 requirement, but does not export any transitive 
Servlet API dependencies to prevent runtime version conflicts with whatever Servlet environment you deploy to. 

This should not affect most users since this library is likely to be used in a Servlet environment where the Servlet 
API is on the classpath at runtime, however if you receive `NoClassDefFoundError`s related to Servlet API classes then 
you'll need to pull a Servlet API dependency into your project that supports a minimum Servlet 2.3 version. For 
reference, `wingtips-old-servlet-api` uses the compile-only Servlet API dependency 
[`javax.servlet:servlet-api:2.3`](http://search.maven.org/#artifactdetails%7Cjavax.servlet%7Cservlet-api%7C2.3%7Cjar). 
