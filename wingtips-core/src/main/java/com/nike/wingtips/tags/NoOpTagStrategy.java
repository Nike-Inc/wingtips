package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

public class NoOpTagStrategy <REQ, RES> implements HttpTagStrategy<REQ, RES> {

    @Override
    public void tagSpanWithRequestAttributes(Span span, REQ requestObj) {
        // intentionally do nothing
    }

    @Override
    public void tagSpanWithResponseAttributes(Span span, RES responseObj) {
        // intentionally do nothing
    }

    @Override
    public void handleErroredRequest(Span span, Throwable throwable) {
        // intentionally do nothing
    }

}
