package com.nike.wingtips.springboot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
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

	/**
	 * Wingtips ClientHttpRequestInterceptor which adds proper headers to a request.
	 */
	private static class WingtipsClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
			Span currentSpan = Tracer.getInstance().getCurrentSpan();

			HttpHeaders headers = request.getHeaders();
			headers.add(TraceHeaders.TRACE_ID, currentSpan.getTraceId());
			headers.add(TraceHeaders.SPAN_ID, currentSpan.getSpanId());
			headers.add(TraceHeaders.PARENT_SPAN_ID, currentSpan.getParentSpanId());
			headers.add(TraceHeaders.SPAN_NAME, currentSpan.getSpanName());
			headers.add(TraceHeaders.TRACE_SAMPLED, String.valueOf(currentSpan.isSampleable()));

			return execution.execute(request, body);
		}
	}

}
