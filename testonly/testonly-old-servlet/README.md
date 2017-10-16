# Wingtips - testonly-old-servlet

Wingtips is a distributed tracing solution for Java 7 and greater based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf).

This submodule contains tests to verify that the [wingtips-servlet-api](../../wingtips-servlet-api) module's 
functionality works as expected in old Servlet 2.x environments. We need a separate tests-only module for this because
we need to limit the dependencies at test runtime to force a Servlet 2.x-only environment.

## More Info

See the [base project README.md](../../README.md) and Wingtips repository source code and javadocs for general Wingtips 
information.

## License

Wingtips is released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)