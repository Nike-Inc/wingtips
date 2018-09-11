package com.nike.wingtips.tags;

/**
 * Implementations know how to extract basic HTTP properties from HTTP Request
 * and Response objects.  {@code HttpTagAdapters} are used by a {@code HttpTagStrategy}
 * to extract the necessary tag values.
 * 
 * @author Brandon Currie
 * 
 */
public interface HttpTagAdapter <REQ,RES> {

    /**
     * @return true if the current {@code Span} should be tagged as having an errd state based on the response object. 
     */
    public boolean isErrorResponse(RES response);

    /**
     * @return The full URL of the request
     * 
     * @param request - The request object to be inspected
     */
    public String getRequestUrl(REQ request);
    
    /**
     * @return The URI of the request
     * 
     * @param request - The request object to be inspected
     */
    public String getRequestUri(REQ request);
    
    /**
     * Returns the http status code from the provided response object
     * @param response To be inspected to determine the http status code
     * @return The {@code String} representation of the http status code
     */
    public String getResponseHttpStatus(RES response);

    /**
     * The HTTP Method used. e.g "GET" or "POST" ..
     * @param request The request object to be inspected
     * @return The HTTP Method
     */
    public String getRequestHttpMethod(REQ request);

}
