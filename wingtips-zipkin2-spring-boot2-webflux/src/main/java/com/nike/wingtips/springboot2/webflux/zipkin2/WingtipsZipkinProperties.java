package com.nike.wingtips.springboot2.webflux.zipkin2;

import com.nike.wingtips.zipkin2.WingtipsToZipkinLifecycleListener;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A {@link ConfigurationProperties} companion for {@link WingtipsWithZipkinSpringBoot2WebfluxConfiguration} that
 * allows you to specify the configuration of {@link WingtipsToZipkinLifecycleListener} via your Spring Boot
 * application's properties files. The following properties are supported (NOTE: {@code wingtips.zipkin.base-url} is
 * required - all others are optional and can be left out, however it's highly recommended that you set
 * {@code wingtips.zipkin.service-name} as well):
 * <ul>
 *     <li>
 *         wingtips.zipkin.zipkin-disabled - Disables registering {@link WingtipsToZipkinLifecycleListener} with
 *         Wingtips if and only if this property value is set to true. If false or missing then {@link
 *         WingtipsToZipkinLifecycleListener} will be registered normally.
 *     </li>
 *     <li>
 *         wingtips.zipkin.base-url - <b>(REQUIRED)</b> The base URL of the Zipkin server to send Wingtips spans to.
 *         See <a href="http://zipkin.io/pages/quickstart">the Zipkin server quickstart page</a> for info on how to
 *         easily setup a local Zipkin server for testing (can be done with a single docker command).
 *     </li>
 *     <li>
 *         wingtips.zipkin.service-name - The name of this service, used when sending Wingtips spans to Zipkin. See
 *         the {@link WingtipsToZipkinLifecycleListener)} constructor javadocs or the
 *         <a href="https://github.com/Nike-Inc/wingtips/tree/master/wingtips-zipkin2">wingtips-zipkin2 readme</a>
 *         for details on how this service name is used. If you don't set this property then {@code "unknown"} will be
 *         used.
 *     </li>
 * </ul>
 *
 * <p>For example you could set the following properties in your {@code application.properties}:
 * <pre>
 *     wingtips.zipkin.zipkin-disabled=false
 *     wingtips.zipkin.base-url=http://localhost:9411
 *     wingtips.zipkin.service-name=some-service-name
 * </pre>
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@ConfigurationProperties("wingtips.zipkin")
public class WingtipsZipkinProperties {
    private boolean zipkinDisabled = false;
    // TODO: Look for a non-empty `spring.application.name` property value before defaulting to `unknown`. See https://github.com/Nike-Inc/wingtips/pull/70#pullrequestreview-136998397
    private String serviceName = "unknown";
    private String baseUrl;

    public boolean shouldApplyWingtipsToZipkinLifecycleListener() {
        return (!zipkinDisabled && serviceName != null && baseUrl != null);
    }

    public boolean isZipkinDisabled() {
        return zipkinDisabled;
    }

    public void setZipkinDisabled(String zipkinDisabled) {
        this.zipkinDisabled = "true".equalsIgnoreCase(zipkinDisabled);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
