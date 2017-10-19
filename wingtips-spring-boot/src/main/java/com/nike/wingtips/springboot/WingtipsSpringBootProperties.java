package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.RequestTracingFilter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
 * </ul>
 *
 * <p>For example you could set the following properties in your {@code application.properties}:
 * <pre>
 *     wingtips.wingtips-disabled=false
 *     wingtips.user-id-header-keys=userid,altuserid
 *     wingtips.span-logging-format=KEY_VALUE
 * </pre>
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Nic Munroe
 */
@Component
@ConfigurationProperties("wingtips")
@SuppressWarnings("WeakerAccess")
public class WingtipsSpringBootProperties {
    private boolean wingtipsDisabled = false;
    private String userIdHeaderKeys;
    private Tracer.SpanLoggingRepresentation spanLoggingFormat;

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
}
