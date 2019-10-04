# Wingtips - wingtips-zipkin

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module was a plugin extension module of the core Wingtips library and contained support for converting Wingtips 
spans to [Zipkin](http://zipkin.io/) spans and sending them to a Zipkin server.

## This Module has Been Removed - Please Migrate to the [wingtips-zipkin2](../wingtips-zipkin2) Dependency

The [wingtips-zipkin2](../wingtips-zipkin2) module replaces this one. It has support for Zipkin v2, while also 
maintaining the capability to send span data to older Zipkin Servers that only understand the Zipkin v1 format. 
Please migrate to the `wingtips-zipkin2` dependency - this `wingtips-zipkin` module has been removed.

Migration should be fairly straightforward for most users - classes moved from the `com.nike.wingtips.zipkin` package
to `com.nike.wingtips.zipkin2`, the "local component name" is no longer needed, and the 
`WingtipsToZipkinLifecycleListener` uses native Zipkin `Reporter`s and `Sender`s to send span data to Zipkin instead of
the custom Wingtips `ZipkinSpanSender` adapter. See the [wingtips-zipkin2 readme](../wingtips-zipkin2) for more details
on the new module and how to use it.
