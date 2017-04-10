package com.nike.wingtips.springboot;

import javax.servlet.Filter;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.servlet.RequestTracingFilterNoAsync;
import com.nike.wingtips.zipkin.WingtipsToZipkinLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Wingtips Spring Boot configuration.
 * It creates tracing web filter bean and adds Zipkin listener if provided with proper configuration.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(ZipkinProperties.class)
public class WingtipsConfiguration {
	private ZipkinProperties zipkinProperties;

	@Autowired
	public WingtipsConfiguration(ZipkinProperties zipkinProperties) {
		this.zipkinProperties = zipkinProperties;
		init();
	}

	/**
	 * Initialize configuration.
	 * Add Zipkin listener if applied in properties.
	 */
	private void init() {
		if (zipkinProperties.apply()) {
			Tracer.getInstance().addSpanLifecycleListener(
				new WingtipsToZipkinLifecycleListener(
					zipkinProperties.getServiceName(),
					zipkinProperties.getLocalComponentNamespace(),
					zipkinProperties.getBaseUrl()
				)
			);
		}
	}

	/**
	 * Create tracing web filter bean.
	 *
	 * @return the tracing web filter bean
	 */
	@Bean
	public Filter getTracingFilter() {
		return new RequestTracingFilterNoAsync();
	}
}
