# Wingtips - spring-boot2-webflux

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
Spring Boot 2 [WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html#webflux) 
environment.

**NOTE:** This module only works with Spring Boot projects that are based on Spring WebFlux, *not* Spring Web MVC
(Servlet-based). If you're looking for Wingtips + Spring Web MVC (Servlet) based Spring Boot support, see the 
[wingtips-spring-boot](../wingtips-spring-boot) module instead. 

## Usage Examples

NOTES:

* The [Wingtips Spring Boot 2 WebFlux sample project](../samples/sample-spring-boot2-webflux) shows these features in 
action.
* All of the features of the [wingtips-spring-webflux](../wingtips-spring-webflux) module are relevant to a 
Spring Boot 2 WebFlux project as well - please see that module's readme for more info on its features.
* More details can be found in the javadocs for the various classes found in this `wingtips-spring-boot2-webflux` 
module.

### Utilizing `WingtipsSpringBoot2WebfluxConfiguration` in a Spring Boot 2 WebFlux application to configure and setup Wingtips tracing

Ensure that the following `@Configuration` gets registered in your Spring Boot app's `ApplicationContext`:

``` java
@Configuration
@Import(WingtipsSpringBoot2WebfluxConfiguration.class)
public class MyAppSpringConfig {
}
``` 

And (optionally) specify configuration from your Spring Boot app's `application.properties`. All of these properties
are optional - you can pick and choose which ones you want to control, and sensible defaults will be chosen for you
for the remaining properties:

``` ini
wingtips.wingtips-disabled=false
wingtips.user-id-header-keys=userid,altuserid
wingtips.span-logging-format=KEY_VALUE
wingtips.server-side-span-tagging-strategy=ZIPKIN
wingtips.server-side-span-tagging-adapter=com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter
```

## Feature details

This `wingtips-spring-boot2-webflux` module contains the following features/classes:

* **`WingtipsSpringBoot2WebfluxConfiguration`** - A Spring `@Configuration` bean that uses 
`@EnableConfigurationProperties` to pull in `WingtipsSpringBoot2WebfluxProperties` (described below) and uses those 
properties to set up the following Wingtips features:
    - Registers the `WingtipsSpringWebfluxWebFilter` filter with Spring, which handles the incoming-request side of 
    distributed tracing for you automatically. See the description in the
    [wingtips-spring-webflux](../wingtips-spring-webflux) module for more details. You can provide your own 
    `WingtipsSpringWebfluxWebFilter` to override the default one if needed - simply register the one you want with 
    Spring's `ApplicationContext` (e.g. via `@Bean public WingtipsSpringWebfluxWebFilter filterOverride() {...}` 
    method) and `WingtipsSpringBoot2WebfluxConfiguration` will use that one instead of creating a new one itself.
    - Sets the span logging representation used by Wingtips to whatever you specify in your 
    `wingtips.span-logging-format` application property (see `WingtipsSpringBoot2WebfluxProperties` description below).
    - The `WingtipsSpringWebfluxWebFilter` uses a `HttpTagAndSpanNamingStrategy` and `HttpTagAndSpanNamingAdapter` to 
    name spans and tag spans with useful metadata about the request and response. By default it will use 
    `ZipkinHttpTagStrategy` and `SpringWebfluxServerRequestTagAdapter`. To modify the tag strategy and/or adapter, you 
    can set the `wingtips.server-side-span-tagging-strategy` and/or `wingtips.server-side-span-tagging-adapter` 
    application properties (see `WingtipsSpringBoot2WebfluxProperties` description below). 
* **`WingtipsSpringBoot2WebfluxProperties`** - The Spring Boot 
[@ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-typesafe-configuration-properties) 
companion for `WingtipsSpringBoot2WebfluxConfiguration` (described above) that allows you to customize some Wingtips 
behaviors from your Spring Boot application's properties files. See the 
[Spring Boot 2 WebFlux sample application's application.properties](../samples/sample-spring-boot2-webflux/src/main/resources/application.properties) 
for a concrete example. The following properties are supported (all of them optional):
    - **`wingtips.wingtips-disabled`** - Disables the Wingtips `WingtipsSpringWebfluxWebFilter` filter if and 
    only if this property value is set to true. If false or missing then `WingtipsSpringWebfluxWebFilter` will be 
    registered normally.
    - **`wingtips.user-id-header-keys`** - Used to specify the user ID header keys that Wingtips will look for on 
    incoming headers. See the `userIdHeaderKeys` parameter javadocs for the 
    `HttpRequestTracingUtils.fromRequestWithHeaders(...)` method for more info. This is optional - if not specified 
    then `WingtipsSpringWebfluxWebFilter` will not extract user ID from incoming request headers but will otherwise 
    function properly.
    - **`wingtips.span-logging-format`** - Determines the format Wingtips will use when logging spans. Represents the 
    `Tracer.SpanLoggingRepresentation` enum. Must be either `JSON` or `KEY_VALUE`. If missing then the span logging 
    format will not be changed (defaults to `JSON`).     
    - **`wingtips.server-side-span-tagging-strategy`** - Determines the `HttpTagAndSpanNamingStrategy` that is used, 
    which in turn determines the set of tags that will be used to record metadata from the request and response.
    These standard tags are often used by visualization tools. This can be one of the short names: `ZIPKIN`, 
    `OPENTRACING`, `NONE`, or `NOOP` (an alias for `NONE`). You can also specify a fully qualified classname to a 
    custom implementation. Custom implementations must extend 
    `HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse>`, and they must have a 
    default no-arg constructor. If this is blank or unset, then `ZIPKIN` will be used as the default.
    - **`wingtips.server-side-span-tagging-adapter`** - Determines the `HttpTagAndSpanNamingAdapter` that will be used 
    to extract tag and span name data from the request/response. This is passed to the 
    `HttpTagAndSpanNamingStrategy` during span naming and tagging. The value of this property should be a fully 
    qualified classname to a specific implementation. Implementations must extend 
    `HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse>`, and they must have a default no-arg 
    constructor. If this is blank or unset, then 
    `com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter` will be used as the default.

For general Wingtips information please see the [base project README.md](../README.md).

## NOTE - `org.springframework:spring-webflux` and `org.springframework.boot:spring-boot-autoconfigure` dependencies required at runtime

This module does not export any transitive Spring or Spring Boot dependencies to prevent version conflicts with 
whatever Spring Boot environment you're running in. 

This should not affect most users since this library is likely to be used in a Spring Boot environment where the 
`spring-webflux` and `spring-boot-autoconfigure` dependencies are already on the classpath at runtime, however if you 
receive class-not-found errors related to classes found in `spring-webflux` or `spring-boot-autoconfigure` then 
you'll need to pull the `org.springframework:spring-webflux` and/or `org.springframework.boot:spring-boot-autoconfigure` 
dependencies into your project. Library authors who wish to build on functionality in this module might need to do 
this.

This module was built using version `5.1.9.RELEASE` of `spring-webflux`, and version `2.1.8.RELEASE` of 
`spring-boot-autoconfigure`, but many other versions of Spring and Spring Boot should work fine, both older and newer.
