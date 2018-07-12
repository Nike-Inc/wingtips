# Wingtips - wingtips-zipkin2

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for converting Wingtips spans 
to [Zipkin](http://zipkin.io/) spans and sending them to a Zipkin server.

### NOTE

This module is an optional plugin for the wingtips-core library. See the [wingtips-core documentation](../README.md) for 
more detailed information on distributed tracing in general and the Wingtips implementation in particular.

## Quickstart

For a basic Zipkin integration that uses the default settings, add the following line as early in your application 
startup procedure as possible (ideally before any requests hit the service that would generate spans) and you'll see 
the Wingtips spans show up in the Zipkin UI:

``` java
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener("some-service-name", 
                                          "http://localhost:9411")
);
```

Parameters for this basic `WingtipsToZipkinLifecycleListener` constructor:

* **`serviceName`** - The name of this service. This is used to build the Zipkin `Endpoint`, which tells Zipkin which 
service generated the spans we're going to send it.
* **`postZipkinSpansBaseUrl`** - The base URL of the Zipkin server. This should include the scheme, host, and port (if 
non-standard for the scheme). e.g. `http://localhost:9411`, or `https://zipkinserver.doesnotexist.com/`.

NOTE: This simple integration assumes you want to send span data to Zipkin over HTTP, and you're sending to a 
Zipkin-v2-API-compatible Zipkin Server (requires version `1.31+` of the Zipkin Server according to the 
[Zipkin Reporter Legacy Encoding docs](https://github.com/openzipkin/zipkin-reporter-java#legacy-encoding)). If you 
need more flexibility in how the span data is sent to your Zipkin Server, or your Zipkin Server only supports 
Zipkin v1 format, then see the sections below on using non-default Zipkin `Reporter`s and `Sender`s.

## Using Non-Default Zipkin `Reporter`s and `Sender`s.

`WingtipsToZipkinLifecycleListener` uses a native Zipkin `Reporter` for sending spans to the Zipkin Server. The default 
under-the-hood behavior when you follow the Quickstart instructions above results in a Zipkin `AsyncReporter` which 
uses a basic Zipkin `URLConnectionSender`. This should work decently for many use cases, however it is limited to HTTP 
transport and expects you to be sending to a Zipkin v2-compatible server (Zipkin Server version `1.31+`).

You're not limited to just the quickstart behavior, however - `WingtipsToZipkinLifecycleListener`'s alternate 
kitchen-sink constructor lets you specify which `Reporter` to use. This means you can take advantage of Zipkin's 
existing `Reporter` and `Sender` ecosystem if you need to customize how spans are sent to Zipkin.

Zipkin has one main `Reporter`: `AsyncReporter`. `AsyncReporter` uses a Zipkin `Sender` to put the encoded spans onto a 
transport like HTTP, so `Sender` is where you get most of your customization. Here's an example for how to create and
register a `WingtipsToZipkinLifecycleListener` with a `Reporter` and `Sender` you define (note that for `spanConverter`
you'd likely use a `new WingtipsToZipkinSpanConverterDefaultImpl()`):

``` java
// Create whatever Sender you want. 
Sender zipkinSpanSenderToUse = ...;

// Create an AsyncReporter that wraps your Sender.
Reporter<zipkin2.Span> zipkinReporterToUse = AsyncReporter
    .builder(zipkinSpanSenderToUse)
    // Extra Reporter customization goes here (if desired) using the AsyncReporter.Builder.
    .build();
    
// Register a WingtipsToZipkinLifecycleListener that uses your Reporter/Sender.    
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener(serviceName, spanConverter, zipkinReporterToUse)
);
```
   
For more information on how to use Zipkin `Reporter`s and `Sender`s see the 
[Official Zipkin Reporter Docs](https://github.com/openzipkin/zipkin-reporter-java). For a list of available `Sender`s 
to choose from do a 
[Maven Search for the `io.zipkin.reporter2` GroupId](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.reporter2%22). 
In particular note that the `OkHttpSender` is also HTTP, but allows you to customize the calls (for example if you need 
to pass auth headers to get through a proxy in front of your Zipkin server).

<a name="zipkin-v1-legacy-encoding-howto"></a>
### My Zipkin Server only supports Zipkin v1 format - how do I deal with that?

If your Zipkin server only supports the Zipkin v1 Span format instead of the Zipkin v2 format that is assumed by 
default in the various `Sender`s, you simply need to specify a `Sender` configured to use Zipkin v1 endpoint, and tell 
the `Reporter` to encode in v1 format. See the "Legacy Encoding" section of the official Zipkin docs 
[here](https://github.com/openzipkin/zipkin-reporter-java#legacy-encoding) for details, but a simple example using the 
basic `URLConnectionSender` would look like this (note that for `spanConverter` you'd likely use a 
`new WingtipsToZipkinSpanConverterDefaultImpl()`):

``` java
// Use the Zipkin v1 endpoint when creating the Sender.
Sender zipkinV1Sender = URLConnectionSender.create("http://localhost:9411/api/v1/spans");

// Use the Zipkin v1 span encoder when creating the Reporter.
Reporter  zipkinV1Reporter = AsyncReporter.builder(zipkinV1Sender).build(SpanBytesEncoder.JSON_V1);

// Register a WingtipsToZipkinLifecycleListener that uses your Zipkin v1 Reporter/Sender.    
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener(serviceName, spanConverter, zipkinV1Reporter)
);
```   

## Using a Non-Default Wingtips->Zipkin Span Converter

The `WingtipsToZipkinLifecycleListener` kitchen-sink constructor also allows you to specify which 
`WingtipsToZipkinSpanConverter` should be used to convert Wingtips spans to Zipkin spans. Normally you probably just
want to use a `new WingtipsToZipkinSpanConverterDefaultImpl()`, but if you have custom needs you can create and use 
your own implementation of `WingtipsToZipkinSpanConverter` that does whatever you want. 
