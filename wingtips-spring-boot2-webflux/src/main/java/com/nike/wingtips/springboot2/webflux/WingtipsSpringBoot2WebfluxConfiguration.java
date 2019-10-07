package com.nike.wingtips.springboot2.webflux;

import com.nike.internal.util.StringUtils;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.tags.OpenTracingHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wingtips Spring Boot 2 WebFlux configuration - this class enables Wingtips tracing on incoming requests by exposing
 * a {@link WingtipsSpringWebfluxWebFilter} as a {@link Bean}, and provides a few other Wingtips configuration
 * features. You can enable the features of this class by registering it with your Spring Boot application's {@link
 * org.springframework.context.ApplicationContext} via {@link org.springframework.context.annotation.Import}, {@link
 * org.springframework.context.annotation.ComponentScan}, or through any of the other mechanisms that register Spring
 * beans. Web app specific beans (like {@link #wingtipsSpringWebfluxWebFilter()}) are marked with {@link
 * ConditionalOnWebApplication} with type {@link ConditionalOnWebApplication.Type#REACTIVE} so that they will be
 * ignored if you are not in a WebFlux application environment.
 *
 * <p>You can override the default {@link WingtipsSpringWebfluxWebFilter} by exposing one of your own in your
 * application Spring config - if you don't specify one yourself then a new {@link WingtipsSpringWebfluxWebFilter}
 * will be used.
 *
 * <p>This class uses {@link WingtipsSpringBoot2WebfluxProperties} to control some behavior options via your
 * application's properties file(s). See that class for full details, but for example you could set the following
 * properties in your {@code application.properties}:
 * <pre>
 *     wingtips.wingtips-disabled=false
 *     wingtips.user-id-header-keys=userid,altuserid
 *     wingtips.span-logging-format=KEY_VALUE
 *     wingtips.server-side-span-tagging-strategy=ZIPKIN
 *     wingtips.server-side-span-tagging-adapter=com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter
 * </pre>
 * None of these properties are required - if they are missing then {@link WingtipsSpringWebfluxWebFilter} will be
 * registered, it will not look for any user ID headers, the span logging format will not be changed (defaults to
 * JSON), and the default tag and span naming strategy and adapter will be used (defaults to {@link
 * ZipkinHttpTagStrategy} and {@link com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter}).
 *
 * <p>If you want Zipkin support in your Wingtips Spring Boot 2 WebFlux application for exporting span data to a
 * Zipkin server, please see {@code WingtipsWithZipkinSpringBoot2WebfluxConfiguration} from the
 * {@code wingtips-zipkin2-spring-boot2-webflux} Wingtips module.
 *
 * @author Nic Munroe
 */
@Configuration
@EnableConfigurationProperties(WingtipsSpringBoot2WebfluxProperties.class)
public class WingtipsSpringBoot2WebfluxConfiguration {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * The project-specific override {@link WingtipsSpringWebfluxWebFilter} that should be used. If a {@link
     * WingtipsSpringWebfluxWebFilter} is not found in the project's Spring app context then this will be initially
     * injected as null and a default {@link WingtipsSpringWebfluxWebFilter} will be created and used.
     *
     * <p>This field injection with {@link Autowired} and {@code required = false} is necessary to allow individual
     * projects the option to override the default without causing an exception in the case that the project does
     * not specify an override.
     */
    @Autowired(required = false)
    protected WingtipsSpringWebfluxWebFilter customSpringWebfluxWebFilter;

    protected WingtipsSpringBoot2WebfluxProperties wingtipsProperties;

    @Autowired
    public WingtipsSpringBoot2WebfluxConfiguration(WingtipsSpringBoot2WebfluxProperties wingtipsProperties) {
        this.wingtipsProperties = wingtipsProperties;
        // Set the span logging representation if specified in the wingtips properties.
        if (wingtipsProperties.getSpanLoggingFormat() != null) {
            Tracer.getInstance().setSpanLoggingRepresentation(wingtipsProperties.getSpanLoggingFormat());
        }
    }

    /**
     * Create and return a {@link WingtipsSpringWebfluxWebFilter}, which will auto-register itself with the
     * Spring Boot 2 WebFlux app as a {@link org.springframework.web.server.WebFilter} and enable Wingtips tracing
     * for incoming requests.
     *
     * <p>NOTE: This will return null (and essentially be a no-op for Spring) in the following cases:
     * <ul>
     *     <li>
     *         When {@link WingtipsSpringBoot2WebfluxProperties#isWingtipsDisabled()} is true. In this case the
     *         application specifically does *not* want a {@link WingtipsSpringWebfluxWebFilter} registered.
     *     </li>
     *     <li>
     *         When {@link #customSpringWebfluxWebFilter} is non-null. In this case the application has a custom
     *         implementation of {@link WingtipsSpringWebfluxWebFilter} that they want to use instead of whatever
     *         default one this method would provide, so we should return null here to allow the custom application
     *         impl to be used.
     *     </li>
     * </ul>
     *
     * @return The {@link WingtipsSpringWebfluxWebFilter} that should be used, or null in the case that
     * {@link WingtipsSpringBoot2WebfluxProperties#isWingtipsDisabled()} is true or if the application already
     * defined a custom override.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public WingtipsSpringWebfluxWebFilter wingtipsSpringWebfluxWebFilter() {
        if (wingtipsProperties.isWingtipsDisabled()) {
            return null;
        }

        // Allow projects to completely override the filter that gets used if desired. If not overridden then create
        //      a new one.
        if (customSpringWebfluxWebFilter != null) {
            return null;
        }

        // We need to create a new one.
        return WingtipsSpringWebfluxWebFilter
            .newBuilder()
            .withUserIdHeaderKeys(extractUserIdHeaderKeysAsList(wingtipsProperties))
            .withTagAndNamingStrategy(extractTagAndNamingStrategy(wingtipsProperties))
            .withTagAndNamingAdapter(extractTagAndNamingAdapter(wingtipsProperties))
            .build();
    }

    protected @Nullable List<String> extractUserIdHeaderKeysAsList(WingtipsSpringBoot2WebfluxProperties props) {
        if (props.getUserIdHeaderKeys() == null) {
            return null;
        }

        return Stream
            .of(props.getUserIdHeaderKeys().split(","))
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .collect(Collectors.toList());
    }

    protected @Nullable HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> extractTagAndNamingStrategy(
        WingtipsSpringBoot2WebfluxProperties props
    ) {
        String strategyName = props.getServerSideSpanTaggingStrategy();

        if (StringUtils.isBlank(strategyName)) {
            // Nothing specified, so return null to use the default.
            return null;
        }

        // Check for a short-name match first.
        if ("zipkin".equalsIgnoreCase(strategyName)) {
            return ZipkinHttpTagStrategy.getDefaultInstance();
        }

        if("opentracing".equalsIgnoreCase(strategyName)) {
            return OpenTracingHttpTagStrategy.getDefaultInstance();
        }

        if("none".equalsIgnoreCase(strategyName) || "noop".equalsIgnoreCase(strategyName)) {
            return NoOpHttpTagStrategy.getDefaultInstance();
        }

        // At this point there was no short-name match. Try instantiating it by classname.
        try {
            //noinspection unchecked
            return (HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse>)
                Class.forName(strategyName).newInstance();
        }
        catch (Exception ex) {
            // Couldn't instantiate by interpreting it as a class name. Return null so the default gets used.
            logger.warn("Unable to match tagging strategy \"{}\". Using the default strategy (Zipkin)",
                        strategyName, ex);
            return null;
        }
    }

    protected @Nullable HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> extractTagAndNamingAdapter(
        WingtipsSpringBoot2WebfluxProperties props
    ) {
        String adapterName = props.getServerSideSpanTaggingAdapter();

        if (StringUtils.isBlank(adapterName)) {
            // Nothing specified, so return null to use the default.
            return null;
        }

        // There are no shortnames for the adapter like there are for strategy. Try instantiating by classname
        try {
            //noinspection unchecked
            return (HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse>)
                Class.forName(adapterName).newInstance();
        }
        catch (Exception ex) {
            // Couldn't instantiate by interpreting it as a class name. Return null so the default gets used.
            logger.warn(
                "Unable to match tagging adapter \"{}\". "
                + "Using the default adapter (SpringWebfluxServerRequestTagAdapter)",
                adapterName, ex
            );
            return null;
        }
    }
}
