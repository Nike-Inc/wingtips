package com.nike.wingtips.springboot2.webflux.zipkin2;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;
import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxProperties;
import com.nike.wingtips.zipkin2.WingtipsToZipkinLifecycleListener;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverterDefaultImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import zipkin2.reporter.Reporter;

/**
 * Wingtips with Zipkin Spring Boot configuration - this is a logical extension of {@link
 * WingtipsSpringBoot2WebfluxConfiguration} that includes support for pushing Wingtips spans to Zipkin via a {@link
 * WingtipsToZipkinLifecycleListener} configured from your application's properties file(s). You can enable this
 * configuration by registering it with your Spring Boot application's {@link
 * org.springframework.context.ApplicationContext} via {@link org.springframework.context.annotation.Import}, {@link
 * org.springframework.context.annotation.ComponentScan}, or through any of the other mechanisms that register Spring
 * beans.
 *
 * <p>This class {@link Import}s {@link WingtipsSpringBoot2WebfluxConfiguration} so all the features it provides are
 * included here, specifically registering {@link WingtipsSpringWebfluxWebFilter} so that Wingtips tracing is enabled
 * for incoming requests and allowing you to configure some behavior options via your application's properties file(s).
 * Please see {@link WingtipsSpringBoot2WebfluxConfiguration} for details.
 *
 * <p>This class uses {@link WingtipsZipkinProperties} to control some behavior options via your application's
 * properties file(s). See that class for full details, but for example you could set the following properties in
 * your {@code application.properties}:
 * <pre>
 *     wingtips.zipkin.zipkin-disabled=false
 *     wingtips.zipkin.base-url=http://localhost:9411
 *     wingtips.zipkin.service-name=some-service-name
 * </pre>
 * Only {@code wingtips.zipkin.base-url} is required - if the other properties are missing then the {@link
 * WingtipsToZipkinLifecycleListener} will still be registered with Wingtips with {@code "unknown"} used for the
 * service name. It's still highly recommended that you set service-name even though it's not strictly required.
 *
 * <p>The properties that control {@link WingtipsSpringBoot2WebfluxConfiguration} are defined in {@link
 * WingtipsSpringBoot2WebfluxProperties}. See the javadocs for those classes for details, but for convenience here's
 * an example of what they might look like in your {@code application.properties}:
 * <pre>
 *     wingtips.wingtips-disabled=false
 *     wingtips.user-id-header-keys=userid,altuserid
 *     wingtips.span-logging-format=KEY_VALUE
 * </pre>
 * None of those properties are required - if they are missing then {@link WingtipsSpringWebfluxWebFilter} will be
 * registered, it will not look for any user ID headers, and JSON span logging format will be used.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@Configuration
@Import({WingtipsSpringBoot2WebfluxConfiguration.class,
         WingtipsWithZipkinSpringBoot2WebfluxConfiguration.DefaultOverrides.class})
@EnableConfigurationProperties(WingtipsZipkinProperties.class)
public class WingtipsWithZipkinSpringBoot2WebfluxConfiguration {

    protected final WingtipsZipkinProperties wingtipsZipkinProperties;
    protected final Reporter<zipkin2.Span> zipkinReporterOverride;
    protected final WingtipsToZipkinSpanConverter zipkinSpanConverterOverride;

    @Autowired
    public WingtipsWithZipkinSpringBoot2WebfluxConfiguration(WingtipsZipkinProperties wingtipsZipkinProperties,
                                                             DefaultOverrides defaultOverrides) {
        this.wingtipsZipkinProperties = wingtipsZipkinProperties;
        this.zipkinReporterOverride = (defaultOverrides == null) ? null : defaultOverrides.zipkinReporter;
        this.zipkinSpanConverterOverride = (defaultOverrides == null) ? null : defaultOverrides.zipkinSpanConverter;
        init();
    }

    /**
     * Initialize configuration.
     * Add Zipkin listener if our {@link WingtipsZipkinProperties} indicates it has the necessary properties specified.
     */
    private void init() {
        if (wingtipsZipkinProperties.shouldApplyWingtipsToZipkinLifecycleListener()) {
            Reporter<zipkin2.Span> zipkinSpanReporter = (zipkinReporterOverride != null)
                ? zipkinReporterOverride
                : WingtipsToZipkinLifecycleListener.generateBasicZipkinReporter(wingtipsZipkinProperties.getBaseUrl());

            WingtipsToZipkinSpanConverter zipkinSpanConverter = (zipkinSpanConverterOverride != null)
                ? zipkinSpanConverterOverride
                : new WingtipsToZipkinSpanConverterDefaultImpl();

            WingtipsToZipkinLifecycleListener listenerToRegister = new WingtipsToZipkinLifecycleListener(
                wingtipsZipkinProperties.getServiceName(),
                zipkinSpanConverter,
                zipkinSpanReporter
            );

            Tracer.getInstance().addSpanLifecycleListener(listenerToRegister);
        }
    }

    public static class DefaultOverrides {
        /**
         * The project-specific override {@link Reporter} that should be used. If a {@link Reporter}
         * is not found in the project's Spring app context then this will be initially injected as null and the default
         * {@link Reporter} will be generated via the basic {@link
         * WingtipsToZipkinLifecycleListener#WingtipsToZipkinLifecycleListener(String, String)} constructor.
         *
         * <p>This field injection with {@link Autowired} and {@code required = false} is necessary to allow individual
         * projects the option to override the default without causing an exception in the case that the project does
         * not specify an override.
         */
        @Autowired(required = false)
        protected Reporter<zipkin2.Span> zipkinReporter;

        /**
         * The project-specific override {@link WingtipsToZipkinSpanConverter} that should be used. If a {@link
         * WingtipsToZipkinSpanConverter} is not found in the project's Spring app context then this will be initially
         * injected as null and a default {@link WingtipsToZipkinSpanConverterDefaultImpl} will be used.
         *
         * <p>This field injection with {@link Autowired} and {@code required = false} is necessary to allow individual
         * projects the option to override the default without causing an exception in the case that the project does
         * not specify an override.
         */
        @Autowired(required = false)
        protected WingtipsToZipkinSpanConverter zipkinSpanConverter;
    }

}
