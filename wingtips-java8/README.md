# Wingtips - wingtips-java8

Wingtips is a distributed tracing solution for Java based on the 
[Google Dapper paper](http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf). 

This module is a plugin extension module of the core Wingtips library and contains support for distributed tracing in a 
Java 8 environment, in particular providing easy ways to support tracing in asynchronous scenarios. The features it 
provides are:

* **`AsyncWingtipsHelper`** - An interface full of helper methods for dealing with asynchronous scenarios. You can use 
`AsyncWingtipsHelper.DEFAULT_IMPL` if you just want the default behavior, or you can create a custom implementation and 
override anything you need different behavior for.
* **`AsyncWingtipsHelperStatic`** - The static version of `AsyncWingtipsHelper` - it has static methods with the same 
method signatures as `AsyncWingtipsHelper`, allowing you to do static method imports to keep your code more readable at 
the expense of flexibility if you ever want to use a non-default implementation.
* All the **`*WithTracing`** classes, e.g. `RunnableWithTracing`, 
`FunctionWithTracing`, `BiConsumerWithTracing`, etc. - These classes wrap their associated 
functional interface from the Java 8 JDK `java.util.function` package. They are what `AsyncWingtipsHelper` uses to 
provide its functionality under the hood, and allow you to easily wrap the functional interfaces (and/or lambdas) so 
that they have the tracing info attached to the thread when they are executed. Most of the common functional interfaces 
are included, but if you run into a use case where you need some that are missing you are encouraged to submit a pull 
request to include them. Note that each of these classes has static factory methods named `withTracing(...)` so you can 
do static method imports to keep your code as clean and readable as possible. The `AsyncWingtipsHelper*` classes
contain disambiguated versions of these factory methods in case you need multiple in the same class, e.g.
`runnableWithTracing(...)` and `functionWithTracing(...)`.

Please make sure you have read the [base project README.md](../README.md). This readme assumes you understand the 
principles and usage instructions described there.

## NOTE

This module builds on the wingtips-core library. See that library's documentation for more detailed information on 
distributed tracing in general and this implementation in particular.

## Usage Examples

(NOTE: Full usage details can be found in the javadocs for `AsyncWingtipsHelper` and the various `*WithTracing` classes)

The following examples of common scenarios show how you can easily use Wingtips in an asynchronous way without a lot of 
hassle with thread local gunk:

* An example of making the current thread's tracing and MDC info hop to a thread executed by an `Executor`:

``` java
import static com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing.withTracing;

// ...

// Just an example - please use an appropriate Executor for your use case.
Executor executor = Executors.newSingleThreadExecutor(); 

executor.execute(withTracing(() -> {
    // Code that needs tracing/MDC wrapping goes here
}));
```

* Or use `ExecutorServiceWithTracing` so you don't forget to wrap your `Runnable`s or `Callable`s (WARNING: be careful
if you have to spin off work that *shouldn't* automatically inherit the calling thread's tracing state, e.g. long-lived
background threads - in those cases you should *not* use an `ExecutorServiceWithTracing` to spin off that work):

``` java
import static com.nike.wingtips.util.asynchelperwrapper.ExecutorServiceWithTracing.withTracing;

// ...

// Just an example - please use an appropriate Executor for your use case.
Executor executor = withTracing(Executors.newSingleThreadExecutor());

executor.execute(() -> {
    // Code that needs tracing/MDC wrapping goes here
});
```

* A similar example using `CompletableFuture`:

``` java
import static com.nike.wingtips.util.asynchelperwrapper.SupplierWithTracing.withTracing;

// ...

CompletableFuture.supplyAsync(withTracing(() -> {
    // Supplier code that needs tracing/MDC wrapping goes here.
    return foo;
}));
```

* There's a `ScheduledExecutorServiceWithTracing` that extends `ExecutorServiceWithTracing` and implements
`ScheduledExecutorService`, for when you need a scheduler that supports automatic Wingtips tracing state propagation.

``` java
import static com.nike.wingtips.util.asynchelperwrapper.ScheduledExecutorServiceWithTracing.withTracing;

// ...

// Just an example - please use an appropriate ScheduledExecutorService for your use case.
ScheduledExecutorService scheduler = withTracing(Executors.newSingleThreadScheduledExecutor());

scheduler.schedule(() -> {
    // Code that needs tracing/MDC wrapping goes here
}, 42, TimeUnit.SECONDS);
```

* This example shows how you might accomplish tasks in an environment where the tracing information is attached
to some request context, and you need to temporarily attach the tracing info in order to do something (e.g. log some
messages with tracing info automatically added using MDC):

``` java
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;

// ...

TracingState tracingInfo = requestContext.getTracingInfo();
runnableWithTracing(
    () -> {
        // Code that needs tracing/MDC wrapping goes here
    },
    tracingInfo
).run();
```

* If you want to use the link and unlink methods manually to wrap some chunk of code, the general procedure looks
like this:

``` java
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

// ...

TracingState originalThreadInfo = null;
try {
    originalThreadInfo = linkTracingToCurrentThread(...);
    // Code that needs tracing/MDC wrapping goes here
}
finally {
    unlinkTracingFromCurrentThread(originalThreadInfo);
}
```

NOTE: Be careful with the last example (manual linking/unlinking). If you fail to guarantee the associated unlink at 
the end then you risk having traces stomp on each other or having other weird interactions occur that you wouldn't 
expect or predict. This can mess up your tracing, so before you use the manual linking/unlinking procedure make sure 
you know what you're doing and test thoroughly in a multi-threaded way under load, and with failure scenarios. For this 
reason it's recommended that you use the `*WithTracing(...)` methods whenever possible instead of manual 
linking/unlinking.

ALSO NOTE: You may have noticed in the examples above that you can import a static factory method named 
`withTracing(...)` for each individual class type, e.g. 
`import static com.nike.wingtips.util.asynchelperwrapper.RunnableWithTracing.withTracing;`, but that there's also a 
similar helper method in the `AsyncWingtipsHelper*` classes, e.g. `runnableWithTracing(...)` from
`import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;`. They ultimately do the exact
same thing - which one you choose is personal preference for readability, although if your class required both
`RunnableWithTracing` and `CallableWithTracing` for example then using the `runnableWithTracing(...)` and 
`callableWithTracing(...)` methods from the `AsyncWingtipsHelper*` helper class would disambiguate the calls and keep
their meaning clear at the cost of a slight increase in verbosity. 