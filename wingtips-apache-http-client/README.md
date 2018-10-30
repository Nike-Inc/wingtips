# Wingtips - apache-http-client

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing 
using [Apache's HttpClient](https://hc.apache.org/httpcomponents-client-ga/index.html) (minimum
[version 4.4](http://search.maven.org/#artifactdetails%7Corg.apache.httpcomponents%7Chttpclient%7C4.4%7Cjar) required).

## Usage Examples

NOTES:

* The `ApacheHttpClientWithWingtipsComponentTest` shows these features in action against a real running server 
(launched as part of the test).
* More details can be found in the javadocs for the various classes found in this `wingtips-apache-http-client` module.

### Instrumenting Apache `HttpClient` using `WingtipsHttpClientBuilder`

This is the preferred mechanism for integrating Wingtips tracing with Apache `HttpClient`. If you control the creation
of the `HttpClientBuilder` that is used to generate `HttpClient`s then use `WingtipsHttpClientBuilder`. If you don't
control which `HttpClientBuilder` is used, then you will need to use the interceptor described in the next section.

``` java
WingtipsHttpClientBuilder builder = WingtipsHttpClientBuilder.create();
// ... call whatever builder methods you need to finish configuring the HttpClient

// Any HttpClient built from a WingtipsHttpClientBuilder will handle Wingtips tracing concerns.
HttpClient httpClient = builder.build();

// Execute requests - any request will have Wingtips tracing info automatically propagated on the request headers.
HttpResponse response = httpClient.execute(...);
``` 

### Instrumenting Apache `HttpClient` using `WingtipsApacheHttpClientInterceptor`

**Manual interceptor addition:**

Note the following to have your Wingtips subspans *fully* surround any other interceptors included in your `HttpClient` 
requests:

* The Wingtips request interceptor should be the *first* `builder.addInterceptorFirst(...)` added. 
* The Wingtips response interceptor should be the *last* `builder.addInterceptorLast(...)` added.

``` java
HttpClientBuilder builder = getBuilderFromSomewhere();
WingtipsApacheHttpClientInterceptor interceptor = new WingtipsApacheHttpClientInterceptor();

builder.addInterceptorFirst((HttpRequestInterceptor)interceptor);
       
// ... other builder setup

builder.addInterceptorLast((HttpResponseInterceptor)interceptor);

// Any HttpClient built from this builder will handle Wingtips tracing concerns.
HttpClient httpClient = builder.build();     

// Execute requests - any request will have Wingtips tracing info automatically propagated on the request headers.
HttpResponse response = httpClient.execute(...);  
```

**Semi-automated interceptor addition:**
 
This semi-automated interceptor addition helper method is functionally equivalent to the manual interceptor addition 
above, except for the potential for other request interceptors (added before) or other response interceptors (added 
after) to not be surrounded by the optional subspan. If you want your Wingtips subspans to fully surround *all* 
interceptors then you either need to use the manual method described above, or use `WingtipsHttpClientBuilder` instead 
of this interceptor. 

In many cases this helper method drawback is inconsequential and the semi-automated helper method works perfectly fine, 
however be aware of this issue in case you have interceptors that are time-consuming or need to interact with Wingtips 
in any way.  

``` java
HttpClientBuilder builder = getBuilderFromSomewhere();

WingtipsApacheHttpClientInterceptor.addTracingInterceptors(builder);

// ... other builder setup

// Any HttpClient built from this builder will handle Wingtips tracing concerns.
HttpClient httpClient = builder.build();     

// Execute requests - any request will have Wingtips tracing info automatically propagated on the request headers.
HttpResponse response = httpClient.execute(...);  
```

## Feature details

This `wingtips-apache-http-client` module contains two classes to perform the same Wingtips tracing tasks. The two
classes are:

* **`WingtipsHttpClientBuilder`** - An extension of Apache's `HttpClientBuilder` that guarantees correct handling of
Wingtips tracing concerns for any `HttpClient` built from it. This should be used instead of the interceptor described
below if possible (i.e. when you control the creation of the `HttpClientBuilder` that is used to generate 
`HttpClient`s).
* **`WingtipsApacheHttpClientInterceptor`** - An implementation of both `HttpRequestInterceptor` and 
`HttpResponseInterceptor`. When properly configured on a `HttpClientBuilder` they can serve the same purpose as
`WingtipsHttpClientBuilder`, however you have to be more careful in using them *and certain exceptions can occur that 
cause the response interceptor to be skipped, leading to dangling spans and broken tracing,* so 
`WingtipsHttpClientBuilder` is preferred when possible. This interceptor should only be used when you don't control the 
creation of the `HttpClientBuilder` that is used to generate `HttpClient`s and you still want a best-effort attempt at 
tracing.   

In either case, the following features are provided:

* Optionally surround requests in a [subspan](../README.md#sub_spans). The subspan option defaults to on and is highly 
recommended since the subspans will provide you with timing info for your downstream calls separate from any parent 
span that may be active at the time the request executes, and includes useful metadata span tags about the request and
response.
    - If the subspan option is enabled but there's no current span on the current thread when the request executes, 
    then a new root span (new trace) will be created rather than a subspan. In either case the newly created span will 
    have a `Span.getSpanPurpose()` of `CLIENT` since the span is for a client HTTP request.
    - By default `ZipkinHttpTagStrategy` and `ApacheHttpClientTagAdapter` will be used to name subspans and 
    automatically tag them with useful tags based on the request and response. You can override which tag/naming 
    strategy and adapter is used during creation of the `WingtipsHttpClientBuilder` or 
    `WingtipsApacheHttpClientInterceptor`.
    - The `Span.getSpanName()` for the newly created span will be generated by an overridable
    `getSubspanSpanName(HttpRequest)` method. This method defers to the tag/naming strategy, with a reasonable 
    fallback in case the tag/naming strategy comes up blank. You can override this `getSubspanSpanName(HttpRequest)` 
    method if you want different logic and you can't (or don't want to) adjust the tag/naming strategy instead.
* Whatever Wingtips `Span` is current at the time the request executes (subspan if the subspan option is on, or 
whatever was current on the thread if the subspan option is off) will be [propagated](../README.md#propagating_traces) 
in the request headers using the logic described by the [B3/Zipkin spec](https://github.com/openzipkin/b3-propagation).
    - This means that no tracing info will be propagated in the case where the subspan option is off and the thread
    executing your Apache `HttpClient` request does not have any current Wingtips span. Turning on the subspan option 
    mitigates this as it guarantees there will be a span to propagate.

For further details on these classes please see their javadocs.

For general Wingtips information please see the [base project README.md](../README.md).

## NOTE - `org.apache.httpcomponents:httpclient` dependency required at runtime

This module does not export any transitive Apache HttpClient dependencies to prevent version conflicts with whatever 
environment your project is running in. 

This should not affect most users since this library is likely to be used in an environment where the `httpclient`
dependency is already on the classpath at runtime, however if you receive class-not-found errors related to 
classes found in `httpclient` then you'll need to pull the `org.apache.httpcomponents:httpclient` dependency into your 
project. Library authors who wish to build on functionality in this module might need to do this.

This module was built using version `4.4.1` of `httpclient`, but other versions of Apache HttpClient should work fine,
both older and newer.
