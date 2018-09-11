package com.nike.wingtips.servlet.tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.nike.wingtips.tags.HttpTagAdapter;

public class ServletRequestTagAdapter implements HttpTagAdapter<HttpServletRequest, HttpServletResponse> {

    /**
     * Tag any span as errd if the response code is >= 500.  Assumes 4xx, and 3xx type errors are graceful, expected
     * use cases. 
     */
    @Override
    public boolean isErrorResponse(HttpServletResponse response) {
        return response.getStatus() >= 500;
    }

    /** 
     * The default is to use {@code request.getRequestURI()}. 
     * Another plausible alternative the full URL without parameters: {@code request.getRequestURL()}
     * 
     * @param request - The {@code HttpServletRequest}
     */
    @Override
    public String getRequestUrl(HttpServletRequest request) {
        return request.getRequestURL().toString();
    }

    @Override
    public String getResponseHttpStatus(HttpServletResponse responseObj) {
        return String.valueOf(responseObj.getStatus());
    }

    @Override
    public String getRequestHttpMethod(HttpServletRequest request) {
        return request.getMethod();
    }

    @Override
    public String getRequestUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

}
