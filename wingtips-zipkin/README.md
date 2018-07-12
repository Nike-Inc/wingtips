# Wingtips - wingtips-zipkin

Wingtips is a distributed tracing solution for Java based on the [Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for converting Wingtips spans to [Zipkin](http://zipkin.io/) spans and sending them to a Zipkin server.

### NOTE

This module is an optional plugin for the wingtips-core library. See the [wingtips-core documentation](../README.md) for more detailed information on distributed tracing in general and the Wingtips implementation in particular.

## This Module is Deprecated - Please Migrate to the [wingtips-zipkin2](../wingtips-zipkin2) Dependency

The `wingtips-zipkin2` module replaces this one. It has support for Zipkin v2, while also maintaining the capability
to send span data to older Zipkin Servers that only understand the Zipkin v1 format. Please migrate to the 
`wingtips-zipkin2` dependency, as this `wingtips-zipkin` module will be dropped in a future update.

Migration should be fairly straightforward for most users - classes moved from the `com.nike.wingtips.zipkin` package
to `com.nike.wingtips.zipkin2`, the "local component name" is no longer needed, and the 
`WingtipsToZipkinLifecycleListener` uses native Zipkin `Reporter`s and `Sender`s to send span data to Zipkin instead of
the custom Wingtips `ZipkinSpanSender` adapter. See the [wingtips-zipkin2 readme](../wingtips-zipkin2) for more details
on the new module and how to use it.

## Quickstart

For a basic Zipkin integration using the minimum-dependencies default implementation provided by this submodule add the following line as early in your application startup procedure as possible (ideally before any requests hit the service that would generate spans) and you'll see the Wingtips spans show up in the Zipkin UI with the proper client-send, client-receive, server-send, and server-receive annotations:

``` java
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener("some-service-name", 
                                          "some-local-component-name", 
                                          "http://localhost:9411")
);
```

Parameters for this basic `WingtipsToZipkinLifecycleListener` constructor:

* **`serviceName`** - The name of this service. This is used to build the Zipkin `Endpoint` that will be used for client/server/local Zipkin annotations when sending spans to Zipkin.
* **`localComponentNamespace`** - The `zipkin.Constants.LOCAL_COMPONENT` namespace that will be used when creating certain Zipkin annotations when the Wingtips span's `Span.getSpanPurpose()` is `LOCAL_ONLY`. See the `zipkin.Constants.LOCAL_COMPONENT` javadocs for more information on what this is and how it's used by the Zipkin server, so you know what value you should send.
* **`postZipkinSpansBaseUrl`** - The base URL of the Zipkin server. This should include the scheme, host, and port (if non-standard for the scheme). e.g. `http://localhost:9411`, or `https://zipkinserver.doesnotexist.com/`.

## Using Native Zipkin Brave `SpanCollector`s

The default `ZipkinSpanSender` implementation provided by this submodule (used under the hood when you follow the Quickstart instructions above) for sending spans to the Zipkin server should work decently for many use cases, however it is limited to HTTP transport and does not have some of the features provided by Zipkin `SpanCollector`s, which serve a similar purpose.

Zipkin's [Brave](https://github.com/openzipkin/brave) libraries provide numerous Zipkin `SpanCollector` implementations that you could use as a Wingtips `ZipkinSpanSender` by creating a simple adapter that implements the `ZipkinSpanSender` interface but uses the concrete Zipkin Brave `SpanCollector` under the hood to do the work.

Simply pull in the Zipkin Brave dependencies into your project that are necessary to have access to the `SpanCollector` implementation you want to use, and create an adapter like the following:

``` java
public class BraveSpanCollectorAdapter implements ZipkinSpanSender {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final HttpSpanCollector braveCollector;
    private final ExecutorService forcePurgeExecutor = Executors.newSingleThreadExecutor();

    public BraveSpanCollectorAdapter(HttpSpanCollector braveCollector) {
        this.braveCollector = braveCollector;
    }

    @Override
    public void handleSpan(zipkin.Span span) {
        try {
            braveCollector.collect(toBraveSpan(span));
        } catch (Throwable ex) {
            logger.warn("An error occurred while adding a span to the Brave collector.", ex);
        }
    }

    @Override
    public void flush() {
        forcePurgeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    braveCollector.flush();
                } catch (Throwable ex) {
                    logger.warn("An error occurred while flushing the Brave collector.", ex);
                }
            }
        });
    }

    public com.twitter.zipkin.gen.Span toBraveSpan(zipkin.Span zipkinSpan) {
        throw new UnsupportedOperationException("Implementation of this method left as an exercise to the reader");
    }
}
```

Once you have an adapter, you can register it with Wingtips like this:

``` java
Tracer.getInstance().addSpanLifecycleListener(
    new WingtipsToZipkinLifecycleListener("some-service-name", 
                                          "some-local-component-name", 
                                          new WingtipsToZipkinSpanConverterImpl(), 
                                          new BraveSpanCollectorAdapter(braveCollectorAdapter))
);
```

The only difference between this and the Quickstart instructions is that here you are using the `WingtipsToZipkinLifecycleListener` constructor that allows you to specify the `WingtipsToZipkinSpanConverter` you want to use (in this case the default implementation) and the `ZipkinSpanSender` you want to use (in this case the adapter backed by a real Zipkin Brave `SpanCollector`).