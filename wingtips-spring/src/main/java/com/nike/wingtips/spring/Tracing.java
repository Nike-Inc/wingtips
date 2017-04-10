package com.nike.wingtips.spring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * Utility class for creating RestTemplate with Wingtips ClientHttpRequestInterceptor already applied.
 * Or you can just create proper Wintips ClientHttpRequestInterceptor.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Tracing {
	/**
	 * Create RestTemplate with Wingtips ClientHttpRequestInterceptor already applied.
	 *
	 * @return RestTemplate instance
	 */
	public static RestTemplate createRestTemplate() {
		RestTemplate template = new RestTemplate();
		List<ClientHttpRequestInterceptor> interceptors = template.getInterceptors();
		if (interceptors == null) {
			interceptors = new ArrayList<>();
			template.setInterceptors(interceptors);
		}
		interceptors.add(createClientHttpRequestInterceptor());
		return template;
	}

	/**
	 * Create Wingtips ClientHttpRequestInterceptor.
	 *
	 * @return Wingtips ClientHttpRequestInterceptor instance.
	 */
	public static ClientHttpRequestInterceptor createClientHttpRequestInterceptor() {
		return new WingtipsClientHttpRequestInterceptor();
	}
}
