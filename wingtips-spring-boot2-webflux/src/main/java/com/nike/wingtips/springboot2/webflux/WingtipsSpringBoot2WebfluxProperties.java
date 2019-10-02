package com.nike.wingtips.springboot2.webflux;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.tags.OpenTracingHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * A {@link ConfigurationProperties} companion for {@link WingtipsSpringBoot2WebfluxConfiguration} that allows you to
 * customize some Wingtips behaviors in your Spring Boot application's properties files. The following properties are
 * supported:
 * <ul>
 *     <li>
 *         wingtips.wingtips-disabled - Disables the Wingtips {@link WingtipsSpringWebfluxWebFilter} Spring WebFlux
 *         filter if and only if this property value is set to true. If false or missing then {@link
 *         WingtipsSpringWebfluxWebFilter} will be registered normally.
 *     </li>
 *     <li>
 *         wingtips.user-id-header-keys - Used to specify the user ID header keys that Wingtips will look for on
 *         incoming headers. See {@link WingtipsSpringWebfluxWebFilter.Builder#withUserIdHeaderKeys(List)} for more
 *         info. This is optional - if not specified then {@link WingtipsSpringWebfluxWebFilter} will not extract
 *         user ID from incoming request headers but will otherwise function properly.
 *     </li>
 *     <li>
 *         wingtips.span-logging-format - Determines the format Wingtips will use when logging spans. Represents the
 *         {@link Tracer.SpanLoggingRepresentation} enum. Must be either JSON or KEY_VALUE. If missing then the span
 *         logging format will not be changed (defaults to JSON).
 *     </li>
 *     <li>
 *         wingtips.server-side-span-tagging-strategy - Represents the {@link
 *         com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy} implementation that should be used by {@link
 *         WingtipsSpringWebfluxWebFilter} to generate span names and automatically set tags on spans that it handles.
 *         {@link WingtipsSpringWebfluxWebFilter} will default to {@link ZipkinHttpTagStrategy}, however you
 *         can pass in a fully qualified class name for this property if you have a custom impl you want to use.
 *         The following short names are also understood:
 *         <ul>
 *             <li>{@code ZIPKIN} - short for {@link ZipkinHttpTagStrategy}</li>
 *             <li>{@code OPENTRACING} - short for {@link OpenTracingHttpTagStrategy}</li>
 *             <li>{@code NONE} or {@code NOOP} - short for {@link com.nike.wingtips.tags.NoOpHttpTagStrategy}</li>
 *         </ul>
 *     </li>
 *     <li>
 *         wingtips.server-side-span-tagging-adapter - Represents the {@link
 *         com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter} implementation that should be passed to the
 *         {@code wingtips.server-side-span-tagging-strategy} when generating span names and tagging spans.
 *         {@link WingtipsSpringWebfluxWebFilter} will default to {@link SpringWebfluxServerRequestTagAdapter},
 *         however you can pass in a fully qualified class name for this property if you have a custom impl you want
 *         to use.
 *     </li>
 * </ul>
 *
 * <p>For example you could set the following properties in your {@code application.properties}:
 * <pre>
 *     wingtips.wingtips-disabled=false
 *     wingtips.user-id-header-keys=userid,altuserid
 *     wingtips.span-logging-format=KEY_VALUE
 *     wingtips.server-side-span-tagging-strategy=ZIPKIN
 *     wingtips.server-side-span-tagging-adapter=com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter
 * </pre>
 *
 * @author Nic Munroe
 */
@ConfigurationProperties("wingtips")
public class WingtipsSpringBoot2WebfluxProperties {
    private boolean wingtipsDisabled = false;
    private String userIdHeaderKeys;
    private Tracer.SpanLoggingRepresentation spanLoggingFormat;
    private String serverSideSpanTaggingStrategy;
    private String serverSideSpanTaggingAdapter;

    public boolean isWingtipsDisabled() {
        return wingtipsDisabled;
    }

    public void setWingtipsDisabled(String wingtipsDisabled) {
        this.wingtipsDisabled = "true".equalsIgnoreCase(wingtipsDisabled);
    }

    public String getUserIdHeaderKeys() {
        return userIdHeaderKeys;
    }

    public void setUserIdHeaderKeys(String userIdHeaderKeys) {
        this.userIdHeaderKeys = userIdHeaderKeys;
    }

    public Tracer.SpanLoggingRepresentation getSpanLoggingFormat() {
        return spanLoggingFormat;
    }

    public void setSpanLoggingFormat(Tracer.SpanLoggingRepresentation spanLoggingFormat) {
        this.spanLoggingFormat = spanLoggingFormat;
    }

    public String getServerSideSpanTaggingStrategy() {
        return serverSideSpanTaggingStrategy;
    }

    public void setServerSideSpanTaggingStrategy(String serverSideSpanTaggingStrategy) {
        this.serverSideSpanTaggingStrategy = serverSideSpanTaggingStrategy;
    }

    public String getServerSideSpanTaggingAdapter() {
        return serverSideSpanTaggingAdapter;
    }

    public void setServerSideSpanTaggingAdapter(String serverSideSpanTaggingAdapter) {
        this.serverSideSpanTaggingAdapter = serverSideSpanTaggingAdapter;
    }
}
