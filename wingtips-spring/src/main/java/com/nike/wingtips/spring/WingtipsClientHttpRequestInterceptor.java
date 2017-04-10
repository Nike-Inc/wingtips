package com.nike.wingtips.spring;

import java.io.IOException;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceHeaders;
import com.nike.wingtips.Tracer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Wingtips ClientHttpRequestInterceptor which adds proper headers to a request.
 */
public class WingtipsClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
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
