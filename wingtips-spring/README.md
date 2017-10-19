# Wingtips - spring

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
[Spring](https://spring.io/) environment. It contains the following features/classes:

* **`WingtipsClientHttpRequestInterceptor`** - An interceptor for Spring's synchronous `RestTemplate` HTTP client that
automatically [propagates](../README.md#propagating_traces) Wingtips tracing information on the downstream call's 
request headers, with an option to surround the downstream call in a [subspan](../README.md#sub_spans).  
* **`WingtipsAsyncClientHttpRequestInterceptor`** - An interceptor for Spring's asynchronous `AsyncRestTemplate` HTTP 
client that automatically [propagates](../README.md#propagating_traces) Wingtips tracing information on the 
downstream call's request headers, with an option to surround the downstream call in a 
[subspan](../README.md#sub_spans). 
* **`ListenableFutureCallbackWithTracing`, `SuccessCallbackWithTracing`, and `FailureCallbackWithTracing`** - These
classes wrap their associated class or functional interface from Spring's `org.springframework.util.concurrent`package.
They can be used to add callbacks to `AsyncRestTemplate` requests (or anywhere else that Spring requires them) so that 
the correct tracing info is attached when the callback runs regardless of which thread it executes on. Note that each 
of these classes has static factory methods named `withTracing(...)` so you can do static method imports to keep your 
code as clean and readable as possible. The `WingtipsSpringUtil` class contains disambiguated versions of these factory 
methods in case you need multiple in the same class, e.g. `successCallbackWithTracing(...)` and 
`failureCallbackWithTracing(...)`.
* **`HttpRequestWrapperWithModifiableHeaders`** - An extension of Spring's `HttpRequestWrapper` that guarantees the
headers are mutable. Used mainly as a helper for the interceptors which need to be able to modify headers, but 
available for public use if you have the same need.
* **`WingtipsSpringUtil`** - A class with static helper methods to facilitate some of the features provided by this 
module. 

For general Wingtips information please see the [base project README.md](../README.md).

## Usage Examples

NOTES: 

* The [Wingtips Spring sample project](../samples/sample-spring-web-mvc) shows these features in action.
* More details can be found in the javadocs for the various classes found in this `wingtips-spring` module.

### `RestTemplate` utilizing `WingtipsClientHttpRequestInterceptor` for automatic tracing propagation
 
``` java
RestTemplate wingtipsEnabledRestTemplate = WingtipsSpringUtil.createTracingEnabledRestTemplate();

// ... later, when ready to execute your request

ResponseEntity<Foo> response = wingtipsEnabledRestTemplate.exchange(...);
// ... process response
``` 

Note that you don't have to use the `WingtipsSpringUtil.createTracingEnabledRestTemplate()` helper method to create a 
Wingtips-enabled `RestTemplate`. If you have a `RestTemplate` already you can call `restTemplate.setInterceptors(...)` 
or `restTemplate.getInterceptors().add(...)` to ensure a `WingtipsClientHttpRequestInterceptor` is present when the
`RestTemplate` executes your requests.  

Also, anything that executes a request will work, including `restTemplate.get*(...)`, `restTemplate.post*(...)`, 
`restTemplate.put(...)`, etc. We use `restTemplate.exchange(...)` above just as an example.

### `AsyncRestTemplate` utilizing `WingtipsAsyncClientHttpRequestInterceptor` and `*WithTracing` callbacks for automatic tracing propagation

``` java
import static com.nike.wingtips.spring.util.WingtipsSpringUtil.failureCallbackWithTracing;
import static com.nike.wingtips.spring.util.WingtipsSpringUtil.successCallbackWithTracing;

// ...

AsyncRestTemplate wingtipsEnabledAsyncRestTemplate = WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate();

// ... later, when ready to execute your request

ListenableFuture<ResponseEntity<Foo>> responseFuture = wingtipsEnabledAsyncRestTemplate.exchange(...);
responseFuture.addCallback(
    successCallbackWithTracing(result -> {
        // ... process successful response
    }),
    failureCallbackWithTracing(error -> {
        // ... process failure
    })
);
```

Note that you don't have to use the `WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate()` helper method to 
create a Wingtips-enabled `AsyncRestTemplate`. If you have a `AsyncRestTemplate` already you can call 
`asyncRestTemplate.setInterceptors(...)` or `asyncRestTemplate.getInterceptors().add(...)` to ensure a 
`WingtipsAsyncClientHttpRequestInterceptor` is present when the `AsyncRestTemplate` executes your requests.  

Also, anything that executes a request will work, including `asyncRestTemplate.get*(...)`, `asyncRestTemplate.post*(...)`, 
`asyncRestTemplate.put(...)`, etc. We use `asyncRestTemplate.exchange(...)` above just as an example.
