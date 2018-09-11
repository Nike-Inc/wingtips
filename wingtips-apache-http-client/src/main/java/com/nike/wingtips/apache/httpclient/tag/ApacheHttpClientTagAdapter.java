package com.nike.wingtips.apache.httpclient.tag;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

import com.nike.wingtips.tags.HttpTagAdapter;

public class ApacheHttpClientTagAdapter implements HttpTagAdapter<HttpRequest, HttpResponse> {

    @Override
    public boolean isErrorResponse(HttpResponse response) {
        return getResponseStatusCode(response) >= 500;
    }

    /**
     * TODO This returns the full URL vs the URI e.g {@code "/endpoint"}
     */
    @Override
    public String getRequestUri(HttpRequest request) {
        return request.getRequestLine().getUri();
    }

    @Override
    public String getResponseHttpStatus(HttpResponse response) {
        return String.valueOf(getResponseStatusCode(response));
    }

    @Override
    public String getRequestHttpMethod(HttpRequest request) {
        return request.getRequestLine().getMethod();
    }

    private int getResponseStatusCode(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    @Override
    public String getRequestUrl(HttpRequest request) {
        return request.getRequestLine().getUri();
    }

}
