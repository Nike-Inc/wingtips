package com.nike.wingtips.tags;

/**
 * Contains constants for Wingtips tags.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class WingtipsTags {

    // Private constructor so it can't be instantiated.
    private WingtipsTags() {}

    /**
     * The short name of the framework handling the Span. e.g. "servlet" for a Servlet-based server
     * framework using a Servlet filter to handle starting the overall request span, or "apache.httpclient" for
     * a child span surrounding an Apache HttpClient call. This helps identify which component of an application
     * was responsible for a given span.
     */
    public static final String SPAN_HANDLER = "span.handler";

}
