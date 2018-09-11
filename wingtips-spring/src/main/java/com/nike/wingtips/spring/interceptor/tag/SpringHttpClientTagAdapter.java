package com.nike.wingtips.spring.interceptor.tag;

import java.io.IOException;
import java.net.MalformedURLException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import com.nike.wingtips.tags.HttpTagAdapter;

public class SpringHttpClientTagAdapter implements HttpTagAdapter<HttpRequest, ClientHttpResponse>{

    /**
     * @return true if the current {@code Span} should be tagged as having an errd state. This defaults to return true
     * if the response status code is &gt;= 500.  
     * 
     * Both the {@code HttpRequest} and {@code ClientHttpResponse} are provided for inspection for other 
     * subclass implementation overrides.
     *  
     * @param response - {@code ClientHttpResponse}
     * @throws IOException - in case of I/O errors while pulling the response status code
     */
    @Override
    public boolean isErrorResponse(ClientHttpResponse response) {
        try {
            return response.getRawStatusCode() >= 500;
        } catch (IOException ioe) {
            return true;
        }
    }

    /**
     * @return The value for the {@code http.url} tag.  The default is to use the full URL.{@code request.getURI().toString()}. 
     * 
     * @param request - The {@code HttpRequest}
     * @throws IOException - in case of I/O errors while pulling the request URI
     */
    @Override
    public String getRequestUrl(HttpRequest request) {
        try {
            return request.getURI().toURL().toString();
        } catch(MalformedURLException exception) {
            // Return the abbreviated version if it can't be converted to a full url
            return request.getURI().toString();
        }
    }

    @Override
    public String getResponseHttpStatus(ClientHttpResponse response) {
        try {
            return String.valueOf(response.getRawStatusCode());
        } catch(IOException ioe) {
            return "IOException";
        }
    }

    @Override
    public String getRequestHttpMethod(HttpRequest request) {
        return request.getMethod().name();
    }

    @Override
    public String getRequestUri(HttpRequest request) {
        return request.getURI().getPath();
    }

}
