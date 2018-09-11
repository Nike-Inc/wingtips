# Wingtips - spring

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
[Spring](https://spring.io/) environment. It contains the following features/classes:

* **`WingtipsClientHttpRequestInterceptor`** - An interceptor for Spring's synchronous `RestTemplate` HTTP client that
automatically [propagates](../README.md#propagating_traces) Wingtips tracing information on the downstream call's 
request headers, with an option to surround the downstream call in a [subspan](../README.md#sub_spans). This interceptor
uses the `OpenTracingTagStrategy` to tag any created subspans. See [Client Request Span Tagging](#client_request_span_tagging) for more details. 
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

<a name="client_request_span_tagging"/>
## Client Request Span Tagging

Both synchronous and asynchronous outbound requests make calls to the provided `HttpTagStrategy` to allow for metadata
to be appended to the span surrounding the request. 

Related: [Server Request Span Tagging](../wingtips-servlet-api/README.md#server_request_span_tagging)

### Default Tagging Strategy

The default implementation uses the `OpenTracingTagStrategy` to append the following tags to the span.

|  Tag  | Description | Example value |
| `http.method` | The request method used. | `GET` |
| `http.url` | The full URL of the request with no request parameters | `http://api.example.com/endpoint` |
| `http.status_code` | The status code from the response | `200` |
| `error` | Only exists if there was an error while making the request, this tag will not be present if there were no errors. Determined by the [SpringHttpClientTagAdapter#isErrorResponse(response)](src/main/java/com/nike/wingtips/spring/interceptor/tag/SpringHttpClientTagAdapter.java) | `true` |

### Defining a Custom Tagging Strategy

The `WingtipsSpringUtil` class exposes methods to generate a `RestTemplate` and an `AsyncRestTemplate` that accept an
`HttpTagStrategy<HttpRequest, ClientHttpResponse>` as a parameter. 
- `public static RestTemplate createTracingEnabledRestTemplate(HttpTagStrategy<HttpRequest, ClientHttpResponse> tagStrategy)`
- `public static AsyncRestTemplate createTracingEnabledAsyncRestTemplate(HttpTagStrategy<HttpRequest, ClientHttpResponse> tagStrategy)`

The tagStrategy provided will be used to append tags for the requests made with the returned rest template. 

#### Example: Use Zipkin tags with RestTemplates

```java
// Example use of a RestTemplate
private String getQuoteFromApi() {
	RestTemplate restTemplate = createTracedRestTemplate();
	Quote quote = restTemplate.getForObject("http://gturnquist-quoters.cfapps.io/api/random", Quote.class);
	return quote.toString();
}

// Get a tracing-enabled RestTemplate
private RestTemplate createTracedRestTemplate() {
    // Tag the subspan with Zipkin tags
	return WingtipsSpringUtil.createTracingEnabledRestTemplate(getZipkinTagStrategy());
}
 
private HttpTagStrategy<HttpRequest, ClientHttpResponse> getZipkinTagStrategy() {
	return new ZipkinTagStrategy<HttpRequest, ClientHttpResponse>(new SpringHttpClientTagAdapter());
}
```

### Changing the logic for an `error` response

It may be desirable to change the logic that determines which spans are tagged with `error`=`true`. By default only responses that 
have a response code >= `500` or have a server-side  exception trying to execute will be flagged as having an error.

The following example generates a `RestTemplate` that will tag any response with a response code >= 400 as having an error while still
maintaining the `OpenTracingTagStrategy`.

```java 
SpringHttpClientTagAdapter errorAdapter = new SpringHttpClientTagAdapter() {
    @Override
    public boolean isErrorResponse(ClientHttpResponse response) {
        try {
            return response.getRawStatusCode() >= 400;
        } catch (IOException ioe) {
            return true;
        }
    }
};
AsyncRestTemplate asyncTemplate = WingtipsSpringUtil.createTracingEnabledAsyncRestTemplate(new OpenTracingTagStrategy<HttpRequest, ClientHttpResponse>(errorAdapter));
```

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

## NOTE - `org.springframework:spring-web` dependency required at runtime

This module does not export any transitive Spring dependencies to prevent version conflicts with whatever Spring 
environment you're running in. 

This should not affect most users since this library is likely to be used in a Spring environment where the `spring-web`
dependency is already on the classpath at runtime, however if you receive class-not-found errors related to Spring 
classes found in `spring-web` then you'll need to pull the `org.springframework:spring-web` dependency into your 
project. Library authors who wish to build on functionality in this module might need to do this.

This module was built using version `4.3.7.RELEASE` of `spring-web`, but many other versions of Spring should work fine,
both older and newer. 