package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

/**
 * Applies {@code Zipkin} standard tags for:
 * <ul>
 * <li>http.method</li>
 * <li>http.path</li>
 * <li>http.url</li>
 * <li>http.status_code</li>
 * <li>error</li>
 * </ul> 
 * 
 * The following known Zipkin tags are <strong>unimplemented</strong> in this strategy:
 * <ul>
 * <li>http.request.size</li>
 * <li>http.response.size</li>
 * <li>http.route</li>
 * <li>http.host</li>
 * </ul>
 * 
 * @author brandon
 *
 * @param <REQ> The expected request object type to be inspected
 * @param <RES> The expected response object type to be inspected
 * 
 * @see <a href='https://github.com/openzipkin/brave/tree/master/instrumentation/http#span-data-policy'>Zipkin's Span Data Policy</a>
 */
public class ZipkinTagStrategy <REQ,RES> implements HttpTagStrategy<REQ,RES> {

    protected HttpTagAdapter<REQ,RES> adapter;

    public ZipkinTagStrategy(HttpTagAdapter<REQ,RES> adapter) {
        this.adapter = adapter;
    }

    @Override
    public void tagSpanWithRequestAttributes(Span span, REQ requestObj) {
        span.putTag(KnownZipkinTags.HTTP_METHOD, adapter.getRequestHttpMethod(requestObj));
        span.putTag(KnownZipkinTags.HTTP_PATH, adapter.getRequestUri(requestObj));
        span.putTag(KnownZipkinTags.HTTP_URL, adapter.getRequestUrl(requestObj));
    }

    @Override
    public void tagSpanWithResponseAttributes(Span span, RES responseObj) {
        span.putTag(KnownZipkinTags.HTTP_STATUS_CODE, adapter.getResponseHttpStatus(responseObj));
        if (adapter.isErrorResponse(responseObj)) {
            addErrorTagToSpan(span);
        }
    }

    @Override
    public void handleErroredRequest(Span span, Throwable throwable) {
        addErrorTagToSpan(span);
    }

    protected void addErrorTagToSpan(Span span) {
        span.putTag(KnownZipkinTags.ERROR, "true");
    }

}
