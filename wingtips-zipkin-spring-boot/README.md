# Wingtips - zipkin-spring-boot

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module was a plugin extension module of the core Wingtips library and contained support for distributed tracing in a 
[Spring Boot](https://spring.io/guides/gs/spring-boot/) environment with [Zipkin](http://zipkin.io/) integration.

## This Module has Been Removed - Please Migrate to the [wingtips-zipkin2-spring-boot](../wingtips-zipkin2-spring-boot) Dependency

The [wingtips-zipkin2-spring-boot](../wingtips-zipkin2-spring-boot) module replaces this one. It has support for 
Zipkin v2, while also maintaining the capability to send span data to older Zipkin Servers that only understand the 
Zipkin v1 format. Please migrate to the `wingtips-zipkin2-spring-boot` dependency - this `wingtips-zipkin-spring-boot` 
module has been removed.

Migration should be fairly straightforward for most users - classes moved from the `com.nike.wingtips.springboot` 
package to `com.nike.wingtips.springboot.zipkin2`, and the "local component name" (and therefore the 
`wingtips.zipkin.local-component-namespace` property) is no longer needed or used. See the 
[wingtips-zipkin2-spring-boot readme](../wingtips-zipkin2-spring-boot) for full details on the new module, as well as
[wingtips-zipkin2](../wingtips-zipkin2) for the underlying Zipkin 2 support.
