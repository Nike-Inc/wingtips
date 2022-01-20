# Wingtips - spring-webflux

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
[Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux) 
environment. In particular it provides a Spring `WebFilter` for handling overall-request spans that your server 
creates in response to incoming requests, and a Spring `ExchangeFilterFunction` to handle HTTP client child-span and 
tracing propagation for Spring's new reactive 
[WebClient](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux-client).
The `WebFilter` is analogous to a Servlet filter from traditional Servlet-based frameworks, and the 
`ExchangeFilterFunction` is analogous to an interceptor for a traditional blocking HTTP client.
 
**NOTE:** As mentioned, this module is for Spring WebFlux specifically, for both serverside and clientside WebFlux 
support. If you're looking for Spring Web MVC support, you should use `RequestTracingFilter` from 
[wingtips-servlet-api](../wingtips-servlet-api) for Servlet-based Spring Web MVC serverside, and the various features 
of [wingtips-spring](../wingtips-spring) for the older Spring HTTP clients.    
 
## Usage Examples

NOTES: 

* The [Wingtips Spring Boot 2 WebFlux sample project](../samples/sample-spring-boot2-webflux) shows these features in 
action.
* More details can be found in the javadocs for the various classes found in this `wingtips-spring-webflux` module.

<a name="wingtips_webfilter_usage"></a>
### Register a `WingtipsSpringWebfluxWebFilter` for automatically handling tracing duties for incoming requests
 
In one of your Spring config classes:
 
``` java
@Bean
public WingtipsSpringWebfluxWebFilter wingtipsSpringWebfluxWebFilter() {
    return WingtipsSpringWebfluxWebFilter
        .newBuilder()
        .withUserIdHeaderKeys(myAppUserIdHeaderKeys) // <- optional
        .build();
}
```

This will register `WingtipsSpringWebfluxWebFilter` as a Spring `WebFilter` so it will handle all requests.

Later, in a controller endpoint:

``` java    
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromContext;
import static com.nike.wingtips.spring.webflux.WingtipsSpringWebfluxUtils.tracingStateFromExchange;

// ...

@GetMapping(SOME_ENDPOINT_PATH)
@ResponseBody
Mono<String> someEndpoint(ServerWebExchange exchange) {
    // The tracing state is embedded in the ServerWebExchange.
    TracingState tracingStateFromExchange = tracingStateFromExchange(exchange);

    return Mono
        .subscriberContext()
        .map(context -> {
            // The tracing state is also embedded and available in the Mono Context.
            TracingState tracingStateFromContext = tracingStateFromContext(context);
            assert (tracingStateFromContext == tracingStateFromExchange); 

            return SOME_ENDPOINT_PAYLOAD;
        });
}
```

As shown above, the correct `TracingState` for the request will be embedded in the `ServerWebExchange` attributes 
for the request, and it will be embedded in the Project Reactor (Mono/Flux) `Context`. You can use this `TracingState` 
with any of the Java 8 helpers from [wingtips-java8](../wingtips-java8) to propagate the `TracingState` when hopping 
threads. See the [wingtips-java8](../wingtips-java8) readme's usage examples for details.

(NOTE: The same `TracingState` _may_ be on the current thread when the controller endpoint executes, or it may not be.
It depends on the thread that Spring ends up using to subscribe to the `Mono`/`Flux`. This can be affected by 
something as simple as a `@RequestBody` annotation. Therefore it's recommended that you not rely on having the
`TracingState` attached to the thread when your endpoint method executes. Instead, you should extract it from the 
`ServerWebExchange` or the Mono/Flux `Context` as shown above, as it will always be available there.)  

### Register a `WingtipsSpringWebfluxExchangeFilterFunction` for automatic tracing propagation when using Spring's reactive `WebClient`

``` java      
// Create a WebClient with the Wingtips filter for automatic subspan and tracing propagation.
WebClient webClientWithWingtips = WebClient
    .builder()
    .filter(WingtipsSpringWebfluxExchangeFilterFunction.DEFAULT_IMPL) // <- or use a constructor with config options.
    .build();

// ... later, when ready to execute your request:           
Mono<ClientResponse> responseMono = webClientWithWingtips
    .get()
    .uri(someRequestUri)
    .attribute(TracingState.class.getName(), overallRequestTracingState)
    .exchange();
```

Note that in order for the `WingtipsSpringWebfluxExchangeFilterFunction` to know what the correct `TracingState` is
that it should use, the correct `TracingState` must be supplied in the request
attributes when the call is executed (i.e. via the `WebClient`'s `exchange()` or `retrieve()` methods). 

To supply the `TracingState` in the request attributes, just provide an attribute with the key of 
`TracingState.class.getName()` and the value of the `TracingState` you want the filter to use (as shown in the example
above). 

As mentioned in the previous `WingtipsSpringWebfluxWebFilter` [usage section](#wingtips_webfilter_usage), you can 
retrieve the overall request `TracingState` (for a server using `WingtipsSpringWebfluxWebFilter`) in two reliable ways:

* Extracted from the `ServerWebExchange` using the `WingtipsSpringWebfluxUtils.tracingStateFromExchange(...)` helper
method.
* Extracted from the Project Reactor Mono/Flux `Context` using the 
`WingtipsSpringWebfluxUtils.tracingStateFromContext(...)` helper method.   

## Feature Details 
 
This module contains the following main features/classes:

* **`WingtipsSpringWebfluxWebFilter`** - A serverside Spring `WebFilter` that handles all of the work for enabling a 
new span when a request comes in and completing it when the request finishes. This filter:
    - Automatically extracts parent span information from the incoming request headers for the new span if available. 
    - Sets the `X-B3-TraceId` response header to the Trace ID for each request.
    - Pulls the user ID from incoming request headers and populates it on the new span for you, assuming you've 
    configured it to look for user ID header(s) (this can be ignored if you don't have user ID headers). 
    - By default, this filter will tag and name spans based on metadata from the request and response using the 
    [ZipkinHttpTagStrategy](../wingtips-core/src/main/java/com/nike/wingtips/tags/ZipkinHttpTagStrategy.java) and
    [SpringWebfluxServerRequestTagAdapter](src/main/java/com/nike/wingtips/spring/webflux/server/WingtipsSpringWebfluxWebFilter.java). 
    You can choose different implementations using the builder if necessary.
    - All configuration options are controlled via the `WingtipsSpringWebfluxWebFilter.Builder`.  
* **`WingtipsSpringWebfluxExchangeFilterFunction`** - A clientside `ExchangeFilterFunction` interceptor for Spring's 
reactive `WebClient` HTTP client that automatically [propagates](../README.md#propagating_traces) Wingtips tracing 
information on the downstream call's request headers, with an option to surround the downstream call in a 
[subspan](../README.md#sub_spans). This interceptor uses a small extension of `ZipkinHttpTagStrategy` by default to 
name and tag any created subspans. See the [Default HTTP Tags](../README.md#default_http_tags) section in the main 
readme for details on what default tags you get, and the javadocs for 
[SpringWebfluxClientRequestZipkinTagStrategy](src/main/java/com/nike/wingtips/spring/webflux/client/SpringWebfluxClientRequestZipkinTagStrategy.java)
for details on the Spring `WebClient` extras. You can use a different tag and naming strategy (and/or tag adapter) if 
desired by passing it in when constructing the interceptor. 
* **`WingtipsSpringWebfluxUtils`** - A class with static helper methods to facilitate some of the features provided by 
this module. In particular for end-user usage:
    - `tracingStateFromExchange(...)` - pulls Wingtips tracing state from a `ServerWebExchange`, populated by
    `WingtipsSpringWebfluxWebFilter`.
    - `tracingStateFromContext(...)` - pulls Wingtips tracing state from a Project Reactor 
    `reactor.util.context.Context`. This is populated by `WingtipsSpringWebfluxWebFilter` for serverside flows, and 
    `WingtipsSpringWebfluxExchangeFilterFunction` for clientside flows.   

For general Wingtips information please see the [base project README.md](../README.md).

## NOTE - `org.springframework:spring-webflux` dependency required at runtime

This module does not export any transitive Spring dependencies to prevent version conflicts with whatever Spring 
environment you're running in. 

This should not affect most users since this library is likely to be used in a Spring environment where the 
`spring-webflux` dependency is already on the classpath at runtime, however if you receive class-not-found errors 
related to Spring WebFlux classes found in `spring-webflux` then you'll need to pull the 
`org.springframework:spring-webflux` dependency into your project. Library authors who wish to build on functionality 
in this module might need to do this.

This module was built using version `5.1.9.RELEASE` of `spring-webflux`, but many other versions of Spring should work 
fine, both older and newer. 