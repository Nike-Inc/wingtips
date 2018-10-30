# Wingtips - zipkin2-spring-boot

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
[Spring Boot](https://spring.io/guides/gs/spring-boot/) environment with [Zipkin](http://zipkin.io/) integration.

## Usage Examples

NOTES: 

* The [Wingtips Spring Boot sample project](../samples/sample-spring-boot) shows these features in action.
* The [wingtips-zipkin2](../wingtips-zipkin2) module readme contains more details on the core Wingtips-with-Zipkin
integration features.
* All of the features of the [wingtips-spring](../wingtips-spring) module are relevant to a Spring Boot 
project as well - please see that module's readme for more info on its features.
* More details can be found in the javadocs for the various classes found in this `wingtips-zipkin2-spring-boot` module.

### Utilizing `WingtipsWithZipkinSpringBootConfiguration` in a Spring Boot application to configure and setup Wingtips tracing with Zipkin integration

Ensure that the following `@Configuration` gets registered in your Spring Boot app's `ApplicationContext`:

``` java
@Configuration
@Import(WingtipsWithZipkinSpringBootConfiguration.class)
public class MyAppSpringConfig {
}
``` 

And specify configuration in your Spring Boot app's `application.properties` (note that all properties are optional
except `wingtips.zipkin.base-url`, which is required if you want the Zipkin integration to work - that said, it's
highly recommended that you also at least specify `wingtips.zipkin.service-name`):

``` ini
# General Wingtips config
wingtips.wingtips-disabled=false
wingtips.user-id-header-keys=userid,altuserid
wingtips.span-logging-format=KEY_VALUE 
wingtips.server-side-span-tagging-strategy=ZIPKIN
# Leave tagging adapter property blank to use the default ServletRequestTagAdapter
wingtips.server-side-span-tagging-adapter=

# Zipkin integration config for Wingtips
wingtips.zipkin.zipkin-disabled=false
wingtips.zipkin.base-url=http://localhost:9411
wingtips.zipkin.service-name=some-service-name
```

### Overriding the default Zipkin `Reporter`

By default, the `WingtipsToZipkinLifecycleListener` that gets registered (when you use 
`WingtipsWithZipkinSpringBootConfiguration`) is setup with a Zipkin `AsyncReporter` that uses a basic 
`URLConnectionSender` to send span data to Zipkin over HTTP. You can easily override this `Reporter` by exposing
a `Reporter` bean somewhere in your Spring app config:

``` java
@Bean
public Reporter<zipkin2.Span> zipkinReporterOverride() {
    // Generate whatever Zipkin Reporter you want Wingtips to use for sending span data to Zipkin.
    Reporter<zipkin2.Span> myReporter = ...; 
    return myReporter;
}
```

If `WingtipsWithZipkinSpringBootConfiguration` detects a non-null Zipkin `Reporter` bean, then that `Reporter` will be 
used. If no `Reporter` override is present, then the default `AsyncReporter` with `URLConnectionSender` will be created
and used.  

### Overriding the default `WingtipsToZipkinSpanConverter`

By default, the `WingtipsToZipkinSpanConverter` that gets registered (when you use 
`WingtipsWithZipkinSpringBootConfiguration`) is a `WingtipsToZipkinSpanConverterDefaultImpl`. You can easily override 
this by exposing a `WingtipsToZipkinSpanConverter` bean somewhere in your Spring app config:

``` java
@Bean
public WingtipsToZipkinSpanConverter wingtipsToZipkinSpanConverterOverride() {
    // Generate whatever WingtipsToZipkinSpanConverter you want for converting Wingtips spans to Zipkin spans.
    WingtipsToZipkinSpanConverter myConverter = ...; 
    return myConverter;
}
```

If `WingtipsWithZipkinSpringBootConfiguration` detects a non-null `WingtipsToZipkinSpanConverter` bean, then that 
`WingtipsToZipkinSpanConverter` will be used. If no `WingtipsToZipkinSpanConverter` override is present, then the 
default `WingtipsToZipkinSpanConverterDefaultImpl` will be created and used.  

## Feature details

This `wingtips-zipkin2-spring-boot` module contains the following features/classes:

* **`WingtipsWithZipkinSpringBootConfiguration`** - A Spring `@Configuration` bean that uses 
`@EnableConfigurationProperties` to pull in `WingtipsZipkinProperties` (described below) and uses those properties to 
set up the following Wingtips-with-Zipkin features:
    - Registers a `WingtipsToZipkinLifecycleListener` with Wingtips' `Tracer` so that Wingtips spans are automatically
    sent to your Zipkin server as they are completed.
    - This class itself does a `@Import(WingtipsSpringBootConfiguration.class)`, so all the config features defined in
    the [wingtips-spring-boot](../wingtips-spring-boot) readme are automatically supported here. The usage example 
    above shows that support via the `wingtips.wingtips-disabled`, `wingtips.user-id-header-keys`, and 
    `wingtips.span-logging-format` properties which are specific to `WingtipsSpringBootConfiguration`. The remaining
    `wingtips.zipkin.*` properties are specific to `WingtipsWithZipkinSpringBootConfiguration`.
* **`WingtipsZipkinProperties`** - The Spring Boot 
[@ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-typesafe-configuration-properties) 
companion for `WingtipsWithZipkinSpringBootConfiguration` (described above) that allows you to customize some 
Wingtips-with-Zipkin integration behaviors from your Spring Boot application's properties files. See the 
[Spring Boot sample application's application.properties](../samples/sample-spring-boot/src/main/resources/application.properties) 
for a concrete example. The following properties are supported:
    - **`wingtips.zipkin.zipkin-disabled`** - Disables registering `WingtipsToZipkinLifecycleListener` with Wingtips 
    if and only if this property value is set to true. If false or missing then `WingtipsToZipkinLifecycleListener` 
    will be registered normally (as long as `wingtips.zipkin.base-url` is specified).
    - **`wingtips.zipkin.base-url`** - *(REQUIRED)* The base URL of the Zipkin server to send Wingtips spans to. This 
    is the only property that is required for `WingtipsWithZipkinSpringBootConfiguration` to be able to setup the
    Zipkin integration - if this is missing then `WingtipsToZipkinLifecycleListener` will not be registered. See 
    the [Zipkin quickstart](http://zipkin.io/pages/quickstart) page for info on how to easily setup a local Zipkin 
    server for testing (can be done with a single docker command).
    - **`wingtips.zipkin.service-name`** - The name of this service, used when sending Wingtips spans to Zipkin. See 
    the [wingtips-zipkin2 readme](../wingtips-zipkin2) for details on how this service name is used. If you don't set
    this property then `"unknown"` will be used. It's highly recommended that you specify this property even though
    it's technically optional.

For general Wingtips information please see the [base project README.md](../README.md).

## NOTE - `org.springframework:spring-web` and `org.springframework.boot:spring-boot-autoconfigure` dependencies required at runtime

This module does not export any transitive Spring or Spring Boot dependencies to prevent version conflicts with 
whatever Spring Boot environment you're running in. 

This should not affect most users since this library is likely to be used in a Spring Boot environment where the 
`spring-web` and `spring-boot-autoconfigure` dependencies are already on the classpath at runtime, however if you 
receive class-not-found errors related to classes found in `spring-web` or `spring-boot-autoconfigure` then 
you'll need to pull the `org.springframework:spring-web` and/or `org.springframework.boot:spring-boot-autoconfigure` 
dependencies into your project. Library authors who wish to build on functionality in this module might need to do 
this.

This module was built using version `4.3.7.RELEASE` of `spring-web`, and version `1.5.2.RELEASE` of 
`spring-boot-autoconfigure`, but many other versions of Spring and Spring Boot should work fine, both older and newer.
