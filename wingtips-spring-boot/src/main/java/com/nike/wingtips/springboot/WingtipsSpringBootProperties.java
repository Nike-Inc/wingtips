package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.tags.OpenTracingHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A {@link ConfigurationProperties} companion for {@link WingtipsSpringBootConfiguration} that allows you to customize
 * some Wingtips behaviors in your Spring Boot application's properties files. The following properties are supported:
 * <ul>
 *     <li>
 *         wingtips.wingtips-disabled - Disables the Wingtips {@link RequestTracingFilter} servlet filter if and only
 *         if this property value is set to true. If false or missing then {@link RequestTracingFilter} will be
 *         registered normally.
 *     </li>
 *     <li>
 *         wingtips.user-id-header-keys - Used to specify the user ID header keys that Wingtips will look for on
 *         incoming headers. See {@link RequestTracingFilter#USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME} for more info.
 *         This is optional - if not specified then {@link RequestTracingFilter} will not extract user ID from incoming
 *         request headers but will otherwise function properly.
 *     </li>
 *     <li>
 *         wingtips.span-logging-format - Determines the format Wingtips will use when logging spans. Represents the
 *         {@link Tracer.SpanLoggingRepresentation} enum. Must be either JSON or KEY_VALUE. If missing then the span
 *         logging format will not be changed (defaults to JSON).
 *     </li>
 *     <li>
 *         wingtips.server-side-span-tagging-strategy - Represents the {@link
 *         com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy} implementation that should be used by {@link
 *         RequestTracingFilter} to generate span names and automatically set tags on spans that it handles.
 *         {@link RequestTracingFilter} will default to {@link ZipkinHttpTagStrategy}, however you
 *         can pass in a fully qualified class name for this property if you have a custom impl you want to use.
 *         The following short names are also understood:
 *         <ul>
 *             <li>{@code ZIPKIN} - short for {@link ZipkinHttpTagStrategy}</li>
 *             <li>{@code OPENTRACING} - short for {@link OpenTracingHttpTagStrategy}</li>
 *             <li>{@code NONE} - short for {@link com.nike.wingtips.tags.NoOpHttpTagStrategy}</li>
 *         </ul>
 *     </li>
 *     <li>
 *         wingtips.server-side-span-tagging-adapter - Represents the {@link
 *         com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter} implementation that should be passed to the
 *         {@code wingtips.server-side-span-tagging-strategy} when generating span names and tagging spans.
 *         {@link RequestTracingFilter} will default to {@link com.nike.wingtips.servlet.tag.ServletRequestTagAdapter},
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
 *     wingtips.server-side-span-tagging-adapter=com.nike.wingtips.servlet.tag.ServletRequestTagAdapter
 * </pre>
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Nic Munroe
 */
@ConfigurationProperties("wingtips")
@SuppressWarnings("WeakerAccess")
public class WingtipsSpringBootProperties {
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
