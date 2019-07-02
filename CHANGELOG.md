# Wingtips Changelog / Release Notes

All notable changes to `Wingtips` will be documented in this file. `Wingtips` adheres to [Semantic Versioning](http://semver.org/).

## Why pre-1.0 releases?

Wingtips is used heavily and is stable internally at Nike, however the wider community may have needs or use cases that we haven't considered. Therefore Wingtips will live at a sub-1.0 version for a short time after its initial open source release to give it time to respond quickly to the open source community without ballooning the version numbers. Once its public APIs have stabilized again as an open source project it will be switched to the normal post-1.0 semantic versioning system.

#### 0.x Releases

- `0.20.x` Releases - [0.20.1](#0201), [0.20.0](#0200)
- `0.19.x` Releases - [0.19.2](#0192), [0.19.1](#0191), [0.19.0](#0190)
- `0.18.x` Releases - [0.18.1](#0181), [0.18.0](#0180)
- `0.17.x` Releases - [0.17.0](#0170)
- `0.16.x` Releases - [0.16.0](#0160)
- `0.15.x` Releases - [0.15.0](#0150) 
- `0.14.x` Releases - [0.14.2](#0142), [0.14.1](#0141), [0.14.0](#0140)
- `0.13.x` Releases - [0.13.0](#0130)
- `0.12.x` Releases - [0.12.1](#0121), [0.12.0](#0120)
- `0.11.x` Releases - [0.11.2](#0112), [0.11.1](#0111), [0.11.0](#0110)
- `0.10.x` Releases - [0.10.0](#0100)
- `0.9.x` Releases - [0.9.0.1](#0901), [0.9.0](#090)

## [0.20.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.20.1)

Released on 2019-07-01.

### Fixed

* The library jars for the `0.20.0` release may not have been published properly. This `0.20.1` release should fix any
problems with the `0.20.0` artifacts.

### Project Build

* Upgraded to Gradle `5.4.1` and got rid of plugins for console summaries.
    - Upgraded by [Nic Munroe][contrib_nicmunroe] in pull request [#106](https://github.com/Nike-Inc/wingtips/pull/106).

## [0.20.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.20.0)

Released on 2019-07-01.

**WARNING - the library jars for this release may not have been published correctly. 
Please use version `0.20.1` instead!** 

### Breaking Changes

* The span's full serialized JSON will no longer be included in the logger MDC by default. Now, only the span's 
trace ID is included in the logger MDC by default. This should only affect you if your logger pattern includes 
`%X{spanJson}` and you rely on that behavior. If you do rely on the full `%X{spanJson}` MDC behavior, you can do the 
following after updating Wingtips to revert to the previous behavior:
`Tracer.getInstance().setSpanFieldsForLoggerMdc(Tracer.SpanFieldForLoggerMdc.TRACE_ID, Tracer.SpanFieldForLoggerMdc.FULL_SPAN_JSON)`.
* `Span.getTags()` and `Span.getTimestampedAnnotations()` now return unmodifiable collections. You must use the other 
methods on `Span` to modify tags/annotations rather than through the collections directly.

### Added

* `Tracer` now has a few new methods for dealing with `SpanLifecycleListener`s: 
`addSpanLifecycleListenerFirst(SpanLifecycleListener)` and `removeAllSpanLifecycleListeners()`. The 
`addSpanLifecycleListenerFirst(...)` method in particular is useful if you need a specific listener to come first 
so that changes it makes can be seen by other listeners.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#97](https://github.com/Nike-Inc/wingtips/pull/97).
* `Tracer` now allows you to select which `Span` fields are included in the logger MDC via the 
`Tracer.setSpanFieldsForLoggerMdc(...)` methods. You can choose from one or more of: trace ID, span ID, parent span ID,
and full span JSON. (Trace ID is now the only field included by default, for performance reasons.)
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#102](https://github.com/Nike-Inc/wingtips/pull/102).
    
### Fixed

* Fixed `Tracer` so that `SpanLifecycleListener.notifySpanCompleted(Span)` is called on all `SpanLifecycleListener`s
after the span is completed but before it is logged. This gives you a chance to make final changes to the span
(e.g. span name, tags, annotations, etc) after it is completed and have those changes reflected in the span info
log output.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#98](https://github.com/Nike-Inc/wingtips/pull/98).
* Improved the precision of `Span.getSpanStartTimeEpochMicros()` for spans that are generated as sub/child spans.
This affects spans generated through `Tracer.startSubSpan(...)`, `Tracer.startSpanInCurrentContext(...)`, and
`Span.generateChildSpan(...)`.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#101](https://github.com/Nike-Inc/wingtips/pull/101).
* Fixed `Span` to clear cached JSON and key/value string serializations after *any* state change (span 
completion, span name change, tags, or timestamped annotations). Previously, only span completion would clear cached
string serializations. NOTE: In order to enforce this behavior, `Span.getTags()` and `Span.getTimestampedAnnotations()`
now return unmodifiable collections. You must use the other methods on `Span` to modify tags/annotations rather than
through the collections directly.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#102](https://github.com/Nike-Inc/wingtips/pull/102).
* Prevented span-to-string serializations for logging when those log statements would be ignored due to having
debug or info logging turned off. This should result in a performance boost in some cases.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#102](https://github.com/Nike-Inc/wingtips/pull/102).
* Fixed span completion to be atomic, guaranteeing that a completed span will only be logged once and 
`SpanLifecycleListener.notifySpanCompleted(Span)` only called once for each listener. Additionally, there are some 
valid asynchronous use cases where multiple completion attempts may occur due to a race condition (i.e. certain types 
of errors triggering simultaneous completion attempts from different areas of code), so this situation is no longer 
considered an error, and will therefore only be logged as a debug warning instead of triggering an exception or 
error log message.  
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#103](https://github.com/Nike-Inc/wingtips/pull/103).
* Fixed some tests throughout the Wingtips codebase that were brittle due to race conditions.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#100](https://github.com/Nike-Inc/wingtips/pull/100).    
    
### Changed

* Changed `WingtipsToZipkinSpanConverterDefaultImpl` and `WingtipsToLightStepLifecycleListener` to add sanitized
IDs as `sanitized_[trace|span|parent]_id` tags on the Wingtips `Span` rather than as separate log messages. This should
reduce log spam if you're receiving trace/span/parent IDs in an invalid format, it'll be easier to correlate sanitized
IDs with the span they're associated with, and will have no effect for requests where the IDs don't need sanitization.
    - Changed by [Nic Munroe][contrib_nicmunroe] in pull request [#99](https://github.com/Nike-Inc/wingtips/pull/99).
* Changed the verbosity of the default `JRETracer` in `WingtipsToLightStepLifecycleListener` from 4 down to 1. This
will eliminate log spam in the event the `WingtipsToLightStepLifecycleListener` has trouble communicating with the span
ingestor.
    - Changed by [Nic Munroe][contrib_nicmunroe] in pull request [#99](https://github.com/Nike-Inc/wingtips/pull/99). 
* Changed the default logger MDC behavior of Wingtips. The span's full serialized JSON will no longer be included
in the logger MDC by default. This should only affect you if your logger pattern includes `%X{spanJson}` and you
rely on that behavior. Trace ID is now the only field that is included by default. This should result in a performance
boost for many users. 
    - Changed by [Nic Munroe][contrib_nicmunroe] in pull request [#102](https://github.com/Nike-Inc/wingtips/pull/102).
 
### Project Build

* Upgraded to Jacoco `0.8.4`.
    - Upgraded by [Nic Munroe][contrib_nicmunroe] in pull request [#102](https://github.com/Nike-Inc/wingtips/pull/102).
* Changed the Travis CI config to use `openjdk8` instead of `oraclejdk8`. 
    - Changed by [Nic Munroe][contrib_nicmunroe] in pull request [#104](https://github.com/Nike-Inc/wingtips/pull/104).
                                                     
## [0.19.2](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.19.2)

Released on 2019-06-14.

### Added

* `Span` now implements `Serializable`. This is needed for Flink, Spark, and similar use cases.
    - Added by [Gregg Hernandez][contrib_gregghz] in pull request [#95](https://github.com/Nike-Inc/wingtips/pull/95).

## [0.19.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.19.1)

Released on 2019-04-25.

### Fixed

* Fixed `WingtipsToLightStepLifecycleListener` to set the LightStep trace and span IDs to always match the Wingtips
trace and span IDs. Also cleaned up a few unnecessary tags.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#93](https://github.com/Nike-Inc/wingtips/pull/93).

## [0.19.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.19.0)

Released on 2019-04-24.

### Added

* Added a `WingtipsToLightStepLifecycleListener` for sending Wingtips spans to LightStep satellites for ingestion. 
    - Added by [Parker Edwards][contrib_parkeredwards] and [Nic Munroe][contrib_nicmunroe] in pull requests 
    [#89](https://github.com/Nike-Inc/wingtips/pull/89) and [#90](https://github.com/Nike-Inc/wingtips/pull/90).

### Fixed

* Optimized the ID sanitization logic done by `WingtipsToZipkinSpanConverterDefaultImpl` and
`WingtipsToLightStepLifecycleListener` for speed/performance. If you receive a heavy volume of traffic with IDs that
need to be sanitized, then this change should significantly reduce the CPU hit caused by sanitization.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#91](https://github.com/Nike-Inc/wingtips/pull/91).

## [0.18.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.18.1)

Released on 2018-11-06.

### Fixed

* Fixed Span key/value serialization format so that it will now unicode-escape any commas it finds in tag keys. This
is in addition to spaces and equals signs, which it was already unicode-escaping for tag keys. This is necessary
to facilitate programmatic parsing of Spans serialized in key/value format, since commas that are not surrounded by
quotes (i.e. not part of a value) are a special character that separates key/value pairs, and tag keys are not 
surrounded by quotes. 
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#86](https://github.com/Nike-Inc/wingtips/pull/86).

## [0.18.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.18.0)

Released on 2018-11-02.

### Fixed

* Fixed a bug that could cause a child span to sometimes inherit the tags of its parent.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#84](https://github.com/Nike-Inc/wingtips/pull/84).

### Added

* Added the ability to add custom timestamped annotations to Spans. See the 
[Custom Timestamped Span Annotations](README.md#custom_annotations) section of the readme for details.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#84](https://github.com/Nike-Inc/wingtips/pull/84).
    - Resolves issue [#23](https://github.com/Nike-Inc/wingtips/issues/23) (along with the changes from Wingtips
    version `0.16.0`).
* Added Wingtips annotations to Zipkin spans when utilizing the Wingtips -> Zipkin functionality found in
`wingtips-zipkin2` module (and the deprecated `wingtips-zipkin`). 
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#84](https://github.com/Nike-Inc/wingtips/pull/84).

## [0.17.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.17.0)

Released on 2018-10-30.

### Added

* Added automatic Span tagging to HTTP server and client Wingtips instrumentation. 
    - Affects Servlet instrumentation (`RequestTracingFilter`), Apache HttpClient instrumentation 
    (`WingtipsHttpClientBuilder` and `WingtipsApacheHttpClientInterceptor`), and Spring RestTemplate HTTP client 
    instrumentation (`WingtipsClientHttpRequestInterceptor` and `WingtipsAsyncClientHttpRequestInterceptor`). 
    - By default Zipkin tags and conventions are used (`ZipkinHttpTagStrategy`), however you can provide different
    strategies and adapters if needed.
    - Added by [Brandon Currie][contrib_brandoncurrie] and [Nic Munroe][contrib_nicmunroe] in pull requests 
    [#81](https://github.com/Nike-Inc/wingtips/pull/81) and [#82](https://github.com/Nike-Inc/wingtips/pull/82).     
    - Resolves issue [#21](https://github.com/Nike-Inc/wingtips/issues/21).
    
### Changed

* Changed the default span name format for HTTP instrumentation (Servlet, Spring, and Apache) to follow Zipkin
conventions. In particular, Span names no longer include the full URL. This is because visualization and analytics 
systems like Zipkin parse the span names in order to identify different logical endpoints, but the full URL can be 
high cardinality due to unique IDs or query strings (`http://.../some/path/12345?foo=bar67890`), even though they 
logically point to the same HTTP endpoint (`http://.../some/path/{id}`). Instead, span names now follow the Zipkin 
convention of `HTTP_METHOD /http/route`, where the HTTP route is the low-cardinality URL "template" that logically 
identifies the endpoint, e.g.: `GET /foo/bar/{id}`. The full path and/or URL can be found in the span tags now, 
instead of the span name. Note that the HTTP route/path template is not available for all frameworks and libraries. 
In those cases the span name will simply be: `HTTP_METHOD` (you'll still be able to extract the full path/URL from the 
span tags).  
    - Changed by [Nic Munroe][contrib_nicmunroe] in pull request [#82](https://github.com/Nike-Inc/wingtips/pull/82). 


### Breaking Changes

* The span name format change (described above) is breaking if you were relying on seeing the full URL/path in the span 
name for any reason, i.e. automated parsing.

Code-level breaking changes:

* The `getSubspanSpanName()` method signature for several classes was adjusted to take in two new args for the
span-tag-and-naming strategy and adapter. Classes affected: (Apache) `WingtipsApacheHttpClientInterceptor`, (Apache) 
`WingtipsHttpClientBuilder`, (Spring) `WingtipsClientHttpRequestInterceptor`, and (Spring) 
`WingtipsAsyncClientHttpRequestInterceptor`.
* `WingtipsApacheHttpClientUtil.getSubspanSpanName()` changed to `getFallbackSubspanSpanName()` to better reflect its
purpose as a fallback rather than primary span namer.
* `HttpRequestTracingUtils.getSubspanSpanNameForHttpRequest()` changed to `generateSafeSpanName()` with adjusted
arguments to match the new span naming format.
* `RequestTracingFilter.setupTracingCompletionWhenAsyncRequestCompletes(...)` method signature adjusted to also take 
in the response and span-tag-and-naming strategy and adapter. Same with 
`ServletRuntime.setupTracingCompletionWhenAsyncRequestCompletes(...)`.
* `WingtipsRequestSpanCompletionAsyncListener.completeRequestSpan(...)` method signature adjusted to take `AsyncEvent`
arg.

Most of the breaking changes are to protected methods or methods that aren't likely to be used by end-users, so
these code-level breaking changes should hopefully be invisible to many (most?) Wingtips users. 
   

## [0.16.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.16.0)

Released on 2018-08-28.

### Added

- Added initial support for tagging `Span`s with key/value pairs. These will be included in `Span.toKeyValueString()` 
and `Span.toJSON()` output, so you will see tags when `Tracer` logs `Span`s. These tags are now also sent to Zipkin
when using `WingtipsToZipkinLifecycleListener`. You can take advantage of known Zipkin and/or OpenTracing tag
constants via `KnownZipkinTags` and `KnownOpenTracingTags`. `Span` serialization and deserialization logic (both for
key/value format and JSON format) has been moved to a new `SpanParser` class, so some of the parsing-related methods
and constants that were in `Span` have been deprecated (but not yet removed) in favor of moving to `SpanParser`.
Some more details about tags can be found in the [main readme](README.md#span_tags). NOTE: timestamped annotations
are not yet implemented, and neither has server/client instrumentation been updated to take advantage of tags
(i.e. automatically tagging spans with `http.method`, `http.path`, `http.status_code`, `error`, etc). Those features
and functionality will come later. 
    - Added by [Brandon Currie][contrib_brandoncurrie] and [Nic Munroe][contrib_nicmunroe] in pull requests 
    [#74](https://github.com/Nike-Inc/wingtips/pull/74) and [#78](https://github.com/Nike-Inc/wingtips/pull/78). 
    - Partially resolves issue [#23](https://github.com/Nike-Inc/wingtips/issues/23).
- `wingtips-zipkin2`'s `WingtipsToZipkinSpanConverterDefaultImpl` now has an alternate constructor that sets it up
to "sanitize" IDs (Trace IDs, Span IDs, Parent Span IDs) that aren't Zipkin compatible. This can be necessary when
you are reporting spans from Wingtips to Zipkin via `WingtipsToZipkinLifecycleListener`, but callers into your system
are not sending IDs that conform to the Zipkin B3 spec. When a non-Zipkin-compatible ID is detected and you've 
configured `WingtipsToZipkinSpanConverterDefaultImpl` to sanitize, then the invalid IDs will be converted to valid IDs
in a deterministic way, a correlation log message will be output, and the original bad IDs will be sent to Zipkin as
tags on the span: `invalid.trace_id`, `invalid.span_id`. and/or `invalid.parent_id`. This sanitization feature is
*not* turned on by default - you must explicitly opt-in by creating the span converter using the alternate constructor.
See `WingtipsToZipkinSpanConverterDefaultImpl` for more details.
    - Added by [Long Ton That][contrib_longtonthat] and [Nic Munroe][contrib_nicmunroe] in pull requests 
    [#73](https://github.com/Nike-Inc/wingtips/pull/73) and [#77](https://github.com/Nike-Inc/wingtips/pull/77). 
- `wingtips-zipkin2-spring-boot`'s `WingtipsWithZipkinSpringBootConfiguration` now allows you to override the 
`WingtipsToZipkinSpanConverter` that is used when converting Wingtips spans to Zipkin spans for reporting to Zipkin.
This is accomplished via a `@Bean` that exposes the `WingtipsToZipkinSpanConverter` impl you want used. See the 
[wingtips-zipkin2-spring-boot readme](wingtips-zipkin2-spring-boot#overriding-the-default-wingtipstozipkinspanconverter)
for details.
    - Added by [Long Ton That][contrib_longtonthat] in pull requests [#75](https://github.com/Nike-Inc/wingtips/pull/75)
    and [#76](https://github.com/Nike-Inc/wingtips/pull/76).
    
### Fixed

- Fixed issues around value-escaping when serializing spans to string using `Span.toKeyValueString()` and 
`Span.toJSON()`. For key/value format and JSON format, values are now escaped using minimal JSON-escaping rules.
See [RFC 7159 Section 7](https://tools.ietf.org/html/rfc7159#section-7) for full details on these rules, but in 
particular note that we are only escaping the bare minimum required characters: quotation mark, reverse solidus 
(backslash), and the control characters (U+0000 through U+001F). Additionally, for key/value format we are now 
surrounding all values with quotes, i.e. `some_key="some JSON-escaped value"`.
    - Fixed by [Brandon Currie][contrib_brandoncurrie] and [Nic Munroe][contrib_nicmunroe] as part of pull requests 
    [#74](https://github.com/Nike-Inc/wingtips/pull/74) and [#78](https://github.com/Nike-Inc/wingtips/pull/78). 

## [0.15.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.15.0)

Released on 2018-07-14.

### Added

- Added [`wingtips-zipkin2`](wingtips-zipkin2) and [`wingtips-zipkin2-spring-boot`](wingtips-zipkin2-spring-boot) 
modules. These serve the same purpose as `wingtips-zipkin` and `wingtips-zipkin-spring-boot` but support the Zipkin 2 
API and span format. You can still send span data to Zipkin 1 API / span format servers with these new modules, so 
they are full replacements and the old modules are now deprecated.
    - Fixed by [Adrian Cole][contrib_adriancole] and [Nic Munroe][contrib_nicmunroe] in pull requests 
    [#69](https://github.com/Nike-Inc/wingtips/pull/69) and [#70](https://github.com/Nike-Inc/wingtips/pull/70).
    For issue [#68](https://github.com/Nike-Inc/wingtips/issues/68). 

### Changed

- Changed the Wingtips Spring, Spring Boot, and Apache HttpClient related modules to no longer export Spring, 
Spring Boot, or Apache HttpClient dependencies. These Wingtips modules aren't likely to be used in an environment
where those dependencies are missing, and this change avoids the Wingtips modules causing version conflicts with
whatever Spring, Spring Boot, or Apache HttpClient version your project is already using. The readmes for these
Wingtips modules have more details in case you were somehow relying on the transitive dependencies.
    - Changed by [Nic Munroe][contrib_nicmunroe] in pull request [#71](https://github.com/Nike-Inc/wingtips/pull/71).

### Deprecated

- Deprecated the [`wingtips-zipkin`](wingtips-zipkin) and [`wingtips-zipkin-spring-boot`](wingtips-zipkin-spring-boot) 
modules in favor of the new [`wingtips-zipkin2`](wingtips-zipkin2) and 
[`wingtips-zipkin2-spring-boot`](wingtips-zipkin2-spring-boot) modules. See the old modules' readmes for deprecation 
and migration details and the new modules' readmes for usage info.
    - Deprecated by [Nic Munroe][contrib_nicmunroe] in pull request [#70](https://github.com/Nike-Inc/wingtips/pull/70).

### Project Build

- Upgraded to Gradle `4.8`.
    - Done by [Adrian Cole][contrib_adriancole] in pull request [#67](https://github.com/Nike-Inc/wingtips/pull/67).
    
## [0.14.2](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.14.2)

Released on 2018-03-22.

### Changed

- Changed `Span` to implement `Closeable` instead of `AutoCloseable`. `Closeable` extends `AutoCloseable` and the
`Span.close()` method signature did not change, so this has no effect other than to enable using `Span`s with code
that requires a `Closeable`. For example, after this change `Span`s can now be used with Apache Commons 
`IOUtils.closeQuietly(span)`.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#65](https://github.com/Nike-Inc/wingtips/pull/65).
    For issue [#64](https://github.com/Nike-Inc/wingtips/issues/64). 

## [0.14.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.14.1)

Released on 2017-11-14.

### Fixed

- Fixed `WingtipsSpringBootProperties` and `WingtipsZipkinProperties` to not be a Spring `@Component`. This was causing
multiple-bean-definition errors when component scanning those classes. Those springboot autoconfig classes now work
when component scanning or when manually imported into an application.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#61](https://github.com/Nike-Inc/wingtips/pull/61).
- Fixed `WingtipsSpringBootConfiguration` to set `RequestTracingFilter` to the highest precedence so that it will
execute as the first Servlet filter, since overall-request-spans should encompass as much of the request as possible.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#62](https://github.com/Nike-Inc/wingtips/pull/62).
    
## [0.14.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.14.0)

Released on 2017-11-06.

### Added

- Added `ExecutorServiceWithTracing` to automate having tracing state hop threads when using `Executor` or 
`ExecutorService` to do work on async threads. See the 
[async section of the main readme](https://github.com/Nike-Inc/wingtips#async_usage), the 
[readme for the Java 8 module](https://github.com/Nike-Inc/wingtips/tree/master/wingtips-java8), or the javadocs on 
`ExecutorServiceWithTracing` for usage examples. 
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#58](https://github.com/Nike-Inc/wingtips/pull/58).
    
### Removed

- Removed the "auto resurrect tracing state from SLF4J MDC" behavior from `Tracer` when the tracing state doesn't exist 
in `Tracer` explicitly. This was possible with some SLF4J implementations where MDC state is inherited by child threads,
however this could cause spans to hop threads when you don't want them to (e.g. long-lived background threads). We now 
have helper classes and methods for explicitly and intentionally hopping threads in a way that isn't surprising like 
the MDC auto-resurrect behavior so those features should be used instead for async thread-hopping. Note that this 
brings Wingtips in line with Logback, which also intentionally removed the "MDC inheritance in child thread" behavior 
for similar reasons (see the Logback version 1.1.5 notes in the [logback changelog](https://logback.qos.ch/news.html)). 
For Wingtips users that relied on this auto-resurrection-from-MDC behavior in Wingtips please refer to the
[async section of the main readme](https://github.com/Nike-Inc/wingtips#async_usage) and/or the
[readme for the Java 8 module](https://github.com/Nike-Inc/wingtips/tree/master/wingtips-java8) for information on 
explicitly and intentionally causing tracing state to hop threads.
    - Removed by [Nic Munroe][contrib_nicmunroe] in pull request [#57](https://github.com/Nike-Inc/wingtips/pull/57).    
    
## [0.13.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.13.0)

Released on 2017-10-25.

### Added

- Added support for Spring and Spring Boot projects, both on the incoming-request side and 
propagating-tracing-downstream side (via Spring's `RestTemplate` HTTP client). Please see the readmes for the 
[wingtips-spring](wingtips-spring/), [wingtips-spring-boot](wingtips-spring-boot/), and 
[wingtips-zipkin-spring-boot](wingtips-zipkin-spring-boot/) modules for details and usage examples.
    - Added by [Aleš Justin][contrib_alesj] in pull request [#37](https://github.com/Nike-Inc/wingtips/pull/37)
    - and by [Nic Munroe][contrib_nicmunroe] in pull request [#51](https://github.com/Nike-Inc/wingtips/pull/51).
- Added support for the Apache HTTP Client. See the [wingtips-apache-http-client](wingtips-apache-http-client/)
module readme for details and usage examples.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#53](https://github.com/Nike-Inc/wingtips/pull/53).  

### Fixed

- Fixed `RequestTracingFilter` to work properly for both Servlet 2.x and Servlet 3+ environments. Part of this fix 
includes *not* exposing Servlet API as a transitive dependency of the `wingtips-servlet-api` module. This means you may 
need to pull in the Servlet API into your project if it's not already there, although it is usually provided by your 
Servlet container. See the
["Servlet API dependency required at runtime"](wingtips-servlet-api/README.md#servlet_api_required_at_runtime) section
of the `wingtips-servlet-api` readme for details.
    - Fixed by [woldie][contrib_woldie] in pull request [#48](https://github.com/Nike-Inc/wingtips/pull/48)
    - and by [Nic Munroe][contrib_nicmunroe] in pull requests [#49](https://github.com/Nike-Inc/wingtips/pull/49) and
    [#52](https://github.com/Nike-Inc/wingtips/pull/52)
    - and with help from [Adrian Cole][contrib_adriancole], in particular some comments in pull request 
    [#49](https://github.com/Nike-Inc/wingtips/pull/49) that pointed us at the 
    [ServletRuntime](https://github.com/openzipkin/brave/blob/master/instrumentation/servlet/src/main/java/brave/servlet/ServletRuntime.java)
    class from [Brave](https://github.com/openzipkin/brave). 
    
## [0.12.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.12.1)

Released on 2017-09-27.

### Fixed

- Fixed `RequestTracingFilter` to work properly with async servlet requests. Previously the overall request span was
completing instantly when the endpoint method returned rather than waiting for the async request to finish. Tests have
been added that execute against a real running Jetty server equipped with `RequestTracingFilter` and several different
types of servlet endpoints - these tests prevent regression and verify proper Wingtips behavior for the following use 
cases: synchronous/blocking servlet, async servlet, blocking-forwarded servlet (using the request dispatcher to forward 
the request to a different blocking servlet), async-forwarded servlet (using the `AsyncContext.dispatch(...)` method to 
forward the request to a different async servlet), and an async servlet that errors-out due to hitting its request 
timeout.   
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#42](https://github.com/Nike-Inc/wingtips/pull/42). 
    
### Deprecated

- With the fixes to `RequestTracingFilter`, several methods and classes are no longer needed and have been marked 
`@Deprecated`. They will be removed in a future update. Full deprecation/migration instructions are in the `@deprecated` 
javadocs, but here is a short list:
    - `RequestTracingFilterNoAsync` class - this class is no longer needed since `RequestTracingFilter` is no longer
    abstract. Move to using `RequestTracingFilter` directly.
    - `RequestTracingFilter.isAsyncDispatch(HttpServletRequest)` method - this method is no longer needed or used by 
    `RequestTracingFilter`. It remains to prevent breaking subclasses that overrode it, but it will not be used. 
    - `RequestTracingFilter.ERROR_REQUEST_URI_ATTRIBUTE` field - this field is no longer used. If you still need it for
    some reason you can refer to `javax.servlet.RequestDispatcher.ERROR_REQUEST_URI` instead.   
    - Deprecated by [Nic Munroe][contrib_nicmunroe] in pull request [#42](https://github.com/Nike-Inc/wingtips/pull/42).

### Added

- [Added a warning to the readme](https://github.com/Nike-Inc/wingtips#try_with_resources_warning) about error handling 
and potentially confusing/non-obvious behavior when autoclosing `Span`s with `try-with-resources` statements.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#43](https://github.com/Nike-Inc/wingtips/pull/43).
- Added several sample applications that show how Wingtips works in various frameworks and use cases. See their 
respective readmes for more information:
    - [samples/sample-jersey1](samples/sample-jersey1/)
    - [samples/sample-jersey2](samples/sample-jersey2/)
    - [samples/sample-spring-web-mvc](samples/sample-spring-web-mvc/)
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#44](https://github.com/Nike-Inc/wingtips/pull/44). 

## [0.12.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.12.0)

Released on 2017-09-18.

### Added

- Added support for reporting 128-bit trace IDs to Zipkin.
    - Done by [Adrian Cole][contrib_adriancole] in pull request [#34](https://github.com/Nike-Inc/wingtips/pull/34).
- Added `Tracer.getCurrentSpanStackSize()`method.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#38](https://github.com/Nike-Inc/wingtips/pull/38).
- Added Java 7 and Java 8 async usage helpers. Please see the 
[async section of the main readme](https://github.com/Nike-Inc/wingtips#async_usage) and the 
[readme for the new Java 8 module](https://github.com/Nike-Inc/wingtips/tree/master/wingtips-java8) for details. 
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#39](https://github.com/Nike-Inc/wingtips/pull/39).
- Added `AutoCloseable` implementation to `Span` to support usage in Java `try-with-resources` statements. See the
[try-with-resources section of the readme](https://github.com/Nike-Inc/wingtips#try_with_resources_info) for details.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull request [#40](https://github.com/Nike-Inc/wingtips/pull/40). 

### Updated

- Zipkin modules updated to use Zipkin version `1.16.2`.
    - Updated by [Adrian Cole][contrib_adriancole] in pull request [#34](https://github.com/Nike-Inc/wingtips/pull/34).
- Updated SLF4J API dependency to version `1.7.25`.
    - Updated by [Nic Munroe][contrib_nicmunroe] in pull request [#38](https://github.com/Nike-Inc/wingtips/pull/38). 

### Project Build

- Upgraded to Gradle `4.1`.
    - Done by [Nic Munroe][contrib_nicmunroe] in pull request [#38](https://github.com/Nike-Inc/wingtips/pull/38).
- Updated Logback dependency to `1.2.3` (only affects tests).
    - Updated by [Nic Munroe][contrib_nicmunroe] in pull request [#38](https://github.com/Nike-Inc/wingtips/pull/38).
- Changed Travis CI to use oraclejdk8 when building Wingtips.
    - Done by [Nic Munroe][contrib_nicmunroe] in pull request [#38](https://github.com/Nike-Inc/wingtips/pull/38).    
    

## [0.11.2](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.11.2)

Released on 2016-09-17.

### Updated

- 32 character (128 bit) trace IDs are now gracefully handled by throwing away the high bits (any characters left of 16 characters). This allows the tracing system to more flexibly introduce 128bit trace ID support in the future.
    - Updated by [Adrian Cole][contrib_adriancole] in pull request [#28](https://github.com/Nike-Inc/wingtips/pull/28).
- Zipkin modules updated to use Zipkin version 1.11.1
    - Updated by [Adrian Cole][contrib_adriancole] in pull request [#28](https://github.com/Nike-Inc/wingtips/pull/28).

## [0.11.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.11.1)

Released on 2016-09-05.

### Updated

- Zipkin updated to version 1.8.4.
    - Updated by [Adrian Cole][contrib_adriancole] in pull request [#24](https://github.com/Nike-Inc/wingtips/pull/24).

## [0.11.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.11.0)

Released on 2016-08-20.

### Fixed

- Trace and span ID generation changed to conform to Zipkin/B3 standards by encoding IDs in lowercase hexadecimal format.
    - Fixed by [Nic Munroe][contrib_nicmunroe] in pull request [#16](https://github.com/Nike-Inc/wingtips/pull/16). For issues [#14](https://github.com/Nike-Inc/wingtips/issues/14) and [#15](https://github.com/Nike-Inc/wingtips/issues/15).

## [0.10.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.10.0)

Released on 2016-08-11.

### Added

- Zipkin integration.
    - Added by [Nic Munroe][contrib_nicmunroe] in pull requests [#7](https://github.com/Nike-Inc/wingtips/pull/7), [#8](https://github.com/Nike-Inc/wingtips/pull/8), and [#10](https://github.com/Nike-Inc/wingtips/pull/10). For issue [#9](https://github.com/Nike-Inc/wingtips/issues/9).

## [0.9.0.1](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.9.0.1)

Released on 2016-07-19.

### Added

- Javadoc jar, CONTRIBUTING.md doc, Travis CI badge in readme, Download badge in readme, artifact publishing support.
    - Added by [Nic Munroe][contrib_nicmunroe].

### Fixed

- Broken Zipkin link.
    - Fixed by [Adrian Cole][contrib_adriancole].

## [0.9.0](https://github.com/Nike-Inc/wingtips/releases/tag/wingtips-v0.9.0)

Released on 2016-06-07.

### Added

- Initial open source code drop for Wingtips.
    - Added by [Nic Munroe][contrib_nicmunroe].
    

[contrib_nicmunroe]: https://github.com/nicmunroe
[contrib_adriancole]: https://github.com/adriancole
[contrib_woldie]: https://github.com/woldie
[contrib_alesj]: https://github.com/alesj
[contrib_longtonthat]: https://github.com/longtonthat
[contrib_brandoncurrie]: https://github.com/brandoncurrie
[contrib_parkeredwards]: https://github.com/parker-edwards
[contrib_gregghz]: https://github.com/gregghz
