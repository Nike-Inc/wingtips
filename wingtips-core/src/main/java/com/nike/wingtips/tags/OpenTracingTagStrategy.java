package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

/**
 * In an effort to match tag patterns consistent with other implementations and with the OpenTracing standards, this class
 * is responsible for adding the following tags to the {@code Span}:
 * <ul>
 *     <li>http.url</li>
 *     <li>http.status_code</li>
 *     <li>http.method</li>
 *     <li>error</li>
 * </ul>
 * 
 * These tag names are based on names provided via OpenTracing's <a href="https://github.com/opentracing/opentracing-java/blob/master/opentracing-api/src/main/java/io/opentracing/tag/Tags.java">Tags.java</a>
 */
public class OpenTracingTagStrategy <REQ,RES> implements HttpTagStrategy<REQ,RES> {

    protected HttpTagAdapter<REQ,RES> adapter;

    public OpenTracingTagStrategy(HttpTagAdapter<REQ,RES> adapter) {
        this.adapter = adapter;
    }

    @Override
    public void tagSpanWithRequestAttributes(Span span, REQ requestObj) {
        span.putTag(KnownOpenTracingTags.HTTP_METHOD, adapter.getRequestHttpMethod(requestObj));
        span.putTag(KnownOpenTracingTags.HTTP_URL, adapter.getRequestUrl(requestObj));
    }

    @Override
    public void tagSpanWithResponseAttributes(Span span, RES responseObj) {
        span.putTag(KnownOpenTracingTags.HTTP_STATUS, adapter.getResponseHttpStatus(responseObj));
        if (adapter.isErrorResponse(responseObj)) {
            addErrorTagToSpan(span);
        }
    }

    @Override
    public void handleErroredRequest(Span span, Throwable throwable) {
        addErrorTagToSpan(span);
    }

    protected void addErrorTagToSpan(Span span) {
        span.putTag(KnownOpenTracingTags.ERROR, "true");
    }


}
