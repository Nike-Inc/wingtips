package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.zipkin.WingtipsToZipkinLifecycleListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wingtips with Zipkin Spring Boot configuration - this is a logical extension of {@link
 * WingtipsSpringBootConfiguration} that includes support for pushing Wingtips spans to Zipkin via a {@link
 * WingtipsToZipkinLifecycleListener} configured from your application's properties file(s). You can enable this
 * configuration by registering it with your Spring Boot application's {@link
 * org.springframework.context.ApplicationContext} via {@link org.springframework.context.annotation.Import}, {@link
 * org.springframework.context.annotation.ComponentScan}, or through any of the other mechanisms that register Spring
 * beans.
 *
 * <p>This class {@link Import}s {@link WingtipsSpringBootConfiguration} so all the features it provides are included
 * here, specifically registering {@link RequestTracingFilter} so that Wingtips tracing is enabled for incoming
 * requests and allowing you to configure some behavior options via your application's properties file(s). Please
 * see {@link WingtipsSpringBootConfiguration} for details.
 *
 * <p>This class uses {@link WingtipsZipkinProperties} to control some behavior options via your application's
 * properties file(s). See that class for full details, but for example you could set the following properties in
 * your {@code application.properties}:
 * <pre>
 *     wingtips.zipkin.zipkin-disabled=false
 *     wingtips.zipkin.base-url=http://localhost:9411
 *     wingtips.zipkin.service-name=some-service-name
 *     wingtips.zipkin.local-component-namespace=some-local-component-name
 * </pre>
 * Only {@code wingtips.zipkin.base-url} is required - if the other properties are missing then the {@link
 * WingtipsToZipkinLifecycleListener} will still be registered with Wingtips with {@code "unknown"} used for the
 * service name and local component namespace.
 *
 * <p>The properties that control {@link WingtipsSpringBootConfiguration} are defined in {@link
 * WingtipsSpringBootProperties}. See the javadocs for those classes for details, but for convenience here's an example
 * of what they might look like in your {@code application.properties}:
 * <pre>
 *     wingtips.wingtips-disabled=false
 *     wingtips.user-id-header-keys=userid,altuserid
 *     wingtips.span-logging-format=KEY_VALUE
 * </pre>
 * None of those properties are required - if they are missing then {@link RequestTracingFilter} will be
 * registered, it will not look for any user ID headers, and JSON span logging format will be used.
 *
 * @deprecated Please migrate to the wingtips-zipkin2-spring-boot dependency.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@Configuration
@Import(WingtipsSpringBootConfiguration.class)
@EnableConfigurationProperties(WingtipsZipkinProperties.class)
@Deprecated
public class WingtipsWithZipkinSpringBootConfiguration {

    @SuppressWarnings("WeakerAccess")
    protected WingtipsZipkinProperties wingtipsZipkinProperties;

    @Autowired
    @SuppressWarnings("WeakerAccess")
    public WingtipsWithZipkinSpringBootConfiguration(WingtipsZipkinProperties wingtipsZipkinProperties) {
        this.wingtipsZipkinProperties = wingtipsZipkinProperties;
        init();
    }

    /**
     * Initialize configuration.
     * Add Zipkin listener if our {@link WingtipsZipkinProperties} indicates it has the necessary properties specified.
     */
    private void init() {
        if (wingtipsZipkinProperties.shouldApplyWingtipsToZipkinLifecycleListener()) {
            Tracer.getInstance().addSpanLifecycleListener(
                new WingtipsToZipkinLifecycleListener(
                    wingtipsZipkinProperties.getServiceName(),
                    wingtipsZipkinProperties.getLocalComponentNamespace(),
                    wingtipsZipkinProperties.getBaseUrl()
                )
            );
        }
    }

}
