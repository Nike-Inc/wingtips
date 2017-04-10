package com.nike.wingtips.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Zipkin Spring Boot properties.
 * See Wingtips-Zipkin README for more info.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@Component
@ConfigurationProperties("zipkin")
public class ZipkinProperties {
	private String serviceName;
	private String localComponentNamespace;
	private String baseUrl;

	public boolean apply() {
		return (serviceName != null && localComponentNamespace != null && baseUrl != null);
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getLocalComponentNamespace() {
		return localComponentNamespace;
	}

	public void setLocalComponentNamespace(String localComponentNamespace) {
		this.localComponentNamespace = localComponentNamespace;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}
}
