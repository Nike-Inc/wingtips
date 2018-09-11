package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

/**
 * There are many libraries that facilitate HTTP Requests.  This interface allows for a
 * consistent approach to tag a span with request and response details without having knowledge
 * of the underlying libraries facilitating the request/response. 
 * 
 * The intent is to allow for easy implementation by surrounding an HTTP call using
 * a pattern as follows within an intercepter or filter:
 * <pre>
 * execute(RequestObj request) {
       try {
           ...
           tagStrategy.tagSpanWithRequestAttributes(span, request);
           Response response = execution.execute(request);
           tagStrategy.tagSpanWithResponseAttributes(span, response);
           return response;
           ...
       } catch(Throwable t) {
           tagStrategy.handleErroredRequest(span, t);
           throw t;
       } finally {...}
   }
 * </pre>
 * 
 * @author Brandon Currie
 *
 * @param <REQ> The object representing the http request
 * @param <RES> The object representing the http response
 */
public interface HttpTagStrategy <REQ, RES> {

    public abstract void tagSpanWithRequestAttributes(Span span, REQ requestObj);

    public abstract void tagSpanWithResponseAttributes(Span span, RES responseObj);

    public abstract void handleErroredRequest(Span span, Throwable throwable);
}
