# Wingtips - jersey2

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
[Jersey 2](https://jersey.github.io/) environment.

## Usage Examples

NOTES: 

* The [Wingtips Jersey2 sample project](../samples/sample-jersey2) shows these features in action.
* More details can be found in the javadocs for the various classes found in this `wingtips-jersey2` module.

To utilize `SpanCustomizingApplicationEventListener` for better Span names and an http.route tag, simply register
`SpanCustomizingApplicationEventListener.create()` or otherwise inject `SpanCustomizingApplicationEventListener` 
into your Jersey 2 server like you would any other Jersey 2 `ApplicationEventListener`/`RequestEventListener`.

IMPORTANT: It's expected that you're using the Wingtips `RequestTracingFilter` (from the 
[wingtips-servlet-api](../wingtips-servlet-api) module) to provide core Wingtips tracing support. 
`SpanCustomizingApplicationEventListener` won't do much for you unless you're using `RequestTracingFilter`. 

## Feature details

The main feature of this module is `SpanCustomizingApplicationEventListener`, which is a 
[Jersey 2 `ApplicationEventListener` and `RequestEventListener`](https://jersey.github.io/documentation/latest/monitoring_tracing.html#d0e16007)
intended to work in concert with the Wingtips `RequestTracingFilter` (from the 
[wingtips-servlet-api](../wingtips-servlet-api) module) in order to give you better Span names and an http.route tag.

Without the `SpanCustomizingApplicationEventListener` in a Jersey 2 environment, your server Span names will simply be 
the HTTP method of the request (i.e. `GET`, `POST`, etc). With `SpanCustomizingApplicationEventListener` added, you'll
also get the URI path template of the request, e.g. `GET /foo/bar/{id}`.  

For general Wingtips information please see the [base project README.md](../README.md).

## NOTE - `org.glassfish.jersey.core:jersey-server` dependency is required at runtime

This module does not export any transitive Jersey 2 dependencies to prevent version conflicts with whatever Jersey 2 
environment you're running in. 

This should not affect most users since this library is likely to be used in a Jersey 2 environment where the 
`jersey-server` dependency is already on the classpath at runtime, however if you receive class-not-found errors 
related to classes found in `jersey-server` then you'll need to pull the `org.glassfish.jersey.core:jersey-server` 
dependency into your project. Library authors who wish to build on functionality in this module might need to do 
this.

This module was built using version `2.23.2` of `jersey-server`, but many other versions of Jersey 2 should work 
fine, both older and newer.
