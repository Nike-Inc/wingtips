# Wingtips - wingtips-servlet-api

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
Java Servlet environment. The features it provides are:

* **HttpSpanFactory** - Utility class that extracts span information from incoming `HttpServletRequest` requests.
* **RequestTracingFilter** - A Servlet Filter that handles all of the work for enabling a new span when a request comes 
in and completing it when the request finishes. This filter automatically uses `HttpSpanFactory` to extract parent span 
information from the incoming request headers for the new span if available. Sets the `X-B3-TraceId` response header to 
the Trace ID for each request. Supports Servlet 3 environments (including asynchronous requests) as well as Servlet 2.x 
environments. You can set the `user-id-header-keys-list` servlet filter param if you expect your service to receive any 
request headers that represent a user ID (if you don't have any user ID headers then this can be ignored). By default, 
this filter will tag spans with metadata from the request and response using the [OpenTracingTagStrategy](../wingtips-core/src/main/java/com/nike/wingtips/tags/OpenTracingTagStrategy.java). 

Please make sure you have read the [base project README.md](../README.md). This readme assumes you understand the 
principles and usage instructions described there.

## Usage Example

The following example shows how you might setup the tracing Servlet Filter when the service expects one of two possible 
header keys that represent the user ID of the user making the call: `userid` or `altuserid`.

**Add the following to web.xml**

``` xml
<filter>
    <filter-name>traceFilter</filter-name>
    <filter-class>com.nike.wingtips.servlet.RequestTracingFilter</filter-class>
    <init-param>
        <param-name>user-id-header-keys-list</param-name>
        <param-value>userid,altuserid</param-value>
    </init-param>
    <init-param>
        <param-name>server-side-span-tag-strategy</param-name>
        <param-value>OPENTRACING</param-value>
    </init-param>
</filter>

<filter-mapping>
    <filter-name>traceFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

If your service does not have any user ID headers you can remove the `<init-param>` element entirely or set the 
`<param-value>` to be empty.

The filter will use `OPENTRACING` tag strategy by default if you remove the `<init-param>` for `server-side-span-tag-strategy`.  

That's it for incoming requests. This Filter will do the right thing and start a root span or child span for incoming 
requests (depending on whether or not the caller included tracing headers), add the trace ID to the response as a 
response header, and guarantees completion of the overall request span right before the response is sent.

**Embedded environments**

For embedded Servlet container environments where you may not be using a `web.xml` file to setup Servlet components 
you'll need to register `RequestTracingFilter` in whatever way your Servlet container allows or requires you 
to register Servlet Filters. For example the `Main` classes in the `samples/sample-*` sample projects show how to 
register `RequestTracingFilter` with embedded Jetty.  

### Propagating the Tracing Information to Downstream Systems

This Filter takes care of setting up the overall request span for incoming requests, but propagating the tracing 
information to downstream systems is still your responsibility. When you call another system you must grab the current 
span via `Tracer.getInstance().getCurrentSpan()` and put its field values into the downstream call's request headers 
using the constants in `TraceHeaders` as the header keys. For example:

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

### Server Request Span Tagging

`HttpServletRequests` and `HttpServletResponses` handled by the `RequestTracingFilter` are passed to a `HttpTagStrategy`
to add metadata to a Span in the form of tags. The [OpenTracingTagStrategy](../wingtips-core/src/main/java/com/nike/wingtips/tags/OpenTracingTagStrategy.java)
is default.

#### Using a pre-defined tag strategy

The `HttpTagStrategy` is defined by the init param `server-side-span-tag-strategy`.  Valid values are:
- `OPENTRACING` **default** - Uses the [OpenTracingTagStrategy](../wingtips-core/src/main/java/com/nike/wingtips/tags/OpenTracingTagStrategy.java)
- `WINGTIPS` Uses the [WingtipsTagStrategy](../wingtips-core/src/main/java/com/nike/wingtips/tags/WingtipsTagStrategy.java)
- `NONE` Uses the [NoOpTagStrategy](../wingtips-core/src/main/java/com/nike/wingtips/tags/NoOpTagStrategy.java)

#### Providing your own tag strategy

##### 1. Extend the `RequestTracingFilter` class

To provide a custom tag strategy, extend the `RequestTracingFilter` and override
`protected void initializeTagStrategy(FilterConfig filterConfig)` to return your
tag strategy.  

In this example, the provided strategy extends the OpenTracing implementation to 
add a tag for the exception name:

```java
public class ErrorTaggingRequestTracingFilter extends RequestTracingFilter {
    @Override
    protected HttpTagStrategy<HttpServletRequest, HttpServletResponse> initializeTagStrategy(FilterConfig filterConfig)  {
    		return new OpenTracingTagStrategy<HttpServletRequest, HttpServletResponse> (new ServletRequestTagAdapter()) {

				@Override public void handleErroredRequest(Span span, Throwable throwable) {
					super.handleErroredRequest(span, throwable);
					span.putTag("error.class", throwable.getClass().getName());
				}
    		};
    }    
}
```

##### 2. Use your class in the servlet configuration

``` xml
<filter>
    <filter-name>traceFilter</filter-name>
    <filter-class>com.example.org.filter.ErrorTaggingRequestTracingFilter</filter-class>
    <init-param>
    ...
```

<a name="servlet_api_required_at_runtime"></a>
## NOTE - Servlet API dependency required at runtime

This `wingtips-servlet-api` module does not export any transitive Servlet API dependencies to prevent runtime version 
conflicts with whatever Servlet environment you deploy to. 

This should not affect most users since this library is likely to be used in a Servlet environment where the Servlet 
API is on the classpath at runtime, however if you receive class-not-found errors related to Servlet API classes then 
you'll need to pull a Servlet API dependency into your project. Library authors who wish to build on functionality in
this module might need to do this. Which Servlet API dependency you pull in depends on the type of Servlet environment 
you want to support (Servlet 2.x or Servlet 3+). For example:

* Servlet 3 API dependency: [`javax.servlet:javax.servlet-api:[servlet-3-api-version]`](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22javax.servlet%22%20AND%20a%3A%22javax.servlet-api%22) 
* Servlet 2 API dependency: [`javax.servlet:servlet-api:[servlet-2-api-version]`](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22javax.servlet%22%20AND%20a%3A%22servlet-api%22) 
