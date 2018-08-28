package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Wingtips Spring Boot configuration - this class enables Wingtips tracing on incoming requests by exposing a {@link
 * RequestTracingFilter} as a {@link Bean}, and provides a few other Wingtips configuration features. You can enable
 * the features of this class by registering it with your Spring Boot application's {@link
 * org.springframework.context.ApplicationContext} via {@link org.springframework.context.annotation.Import}, {@link
 * org.springframework.context.annotation.ComponentScan}, or through any of the other mechanisms that register Spring
 * beans. Web app specific beans (like {@link #wingtipsRequestTracingFilter()}) are marked with {@link
 * ConditionalOnWebApplication} so that they will be ignored if you are not in a web application environment.
 *
 * <p>You can override the default {@link RequestTracingFilter} by exposing one of your own in your application Spring
 * config - if you don't specify one yourself then a new {@link RequestTracingFilter} will be used.
 *
 * <p>This class uses {@link WingtipsSpringBootProperties} to control some behavior options via your application's
 * properties file(s). See that class for full details, but for example you could set the following properties in
 * your {@code application.properties}:
 * <pre>
 *     wingtips.wingtips-disabled=false
 *     wingtips.user-id-header-keys=userid,altuserid
 *     wingtips.span-logging-format=KEY_VALUE
 *     wingtips.server-side-span-tagging-strategy=ZIPKIN
 *     wingtips.server-side-span-tagging-adapter=com.nike.wingtips.servlet.tag.ServletRequestTagAdapter
 * </pre>
 * None of these properties are required - if they are missing then {@link RequestTracingFilter} will be
 * registered, it will not look for any user ID headers, the span logging format will not be changed (defaults to
 * JSON), and the default tag and span naming strategy and adapter will be used (defaults to {@link
 * ZipkinHttpTagStrategy} and {@link com.nike.wingtips.servlet.tag.ServletRequestTagAdapter}).
 *
 * <p>If you want Zipkin support in your Wingtips Spring Boot application for exporting span data to a Zipkin server,
 * please see {@code WingtipsWithZipkinSpringBootConfiguration} from the {@code wingtips-zipkin2-spring-boot} Wingtips
 * module.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Nic Munroe
 */
@Configuration
@EnableConfigurationProperties(WingtipsSpringBootProperties.class)
@SuppressWarnings("WeakerAccess")
public class WingtipsSpringBootConfiguration {

    /**
     * The project-specific override {@link RequestTracingFilter} that should be used. If a {@link RequestTracingFilter}
     * is not found in the project's Spring app context then this will be initially injected as null and a default
     * {@link RequestTracingFilter} will be created and used.
     *
     * <p>This field injection with {@link Autowired} and {@code required = false} is necessary to allow individual
     * projects the option to override the default without causing an exception in the case that the project does
     * not specify an override.
     */
    @Autowired(required = false)
    @SuppressWarnings("WeakerAccess")
    protected RequestTracingFilter requestTracingFilter;

    @SuppressWarnings("WeakerAccess")
    protected WingtipsSpringBootProperties wingtipsProperties;

    @Autowired
    public WingtipsSpringBootConfiguration(WingtipsSpringBootProperties wingtipsProperties) {
        this.wingtipsProperties = wingtipsProperties;
        // Set the span logging representation if specified in the wingtips properties.
        if (wingtipsProperties.getSpanLoggingFormat() != null) {
            Tracer.getInstance().setSpanLoggingRepresentation(wingtipsProperties.getSpanLoggingFormat());
        }
    }

    /**
     * Create and return a {@link RequestTracingFilter}, which will auto-register itself with the Spring Boot app as
     * a servlet filter and enable Wingtips tracing for incoming requests.
     *
     * @return The {@link RequestTracingFilter} that should be used.
     */
    @Bean
    @ConditionalOnWebApplication
    public FilterRegistrationBean wingtipsRequestTracingFilter() {
        if (wingtipsProperties.isWingtipsDisabled()) {
            // We can't return null or create a FilterRegistrationBean that has a null filter inside as it will result
            //      in a NullPointerException. So instead we'll return a do-nothing servlet filter.
            return new FilterRegistrationBean(new DoNothingServletFilter());
        }

        // Allow projects to completely override the filter that gets used if desired. If not overridden then create
        //      a new one.
        if (requestTracingFilter == null) {
            requestTracingFilter = new RequestTracingFilter();
        }
        
        FilterRegistrationBean frb = new FilterRegistrationBean(requestTracingFilter);
        // Add the user ID header keys init param if specified in the wingtips properties.
        if (wingtipsProperties.getUserIdHeaderKeys() != null) {
            frb.addInitParameter(
                RequestTracingFilter.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME,
                wingtipsProperties.getUserIdHeaderKeys()
            );
        }
        
        // Add the tagging strategy init param if specified in the wingtips properties.
        if (wingtipsProperties.getServerSideSpanTaggingStrategy() != null) {
            frb.addInitParameter(
                RequestTracingFilter.TAG_AND_SPAN_NAMING_STRATEGY_INIT_PARAM_NAME,
                wingtipsProperties.getServerSideSpanTaggingStrategy()
            );
        }

        // Add the tagging adapter init param if specified in the wingtips properties.
        if (wingtipsProperties.getServerSideSpanTaggingAdapter() != null) {
            frb.addInitParameter(
                RequestTracingFilter.TAG_AND_SPAN_NAMING_ADAPTER_INIT_PARAM_NAME,
                wingtipsProperties.getServerSideSpanTaggingAdapter()
            );
        }

        // Set the order so that the tracing filter is registered first
        frb.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return frb;
    }

    /**
     * A dummy servlet filter that does nothing - it simply calls {@link
     * FilterChain#doFilter(ServletRequest, ServletResponse)} to propagate the request/response down the filter
     * chain.
     */
    protected static class DoNothingServletFilter implements Filter {
        @Override
        @SuppressWarnings("RedundantThrows")
        public void init(FilterConfig filterConfig) throws ServletException { }

        @Override
        public void doFilter(ServletRequest request,
                             ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() { }
    }

}
