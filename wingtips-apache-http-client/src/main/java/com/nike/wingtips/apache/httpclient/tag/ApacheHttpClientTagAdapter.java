package com.nike.wingtips.apache.httpclient.tag;

import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension of {@link HttpTagAndSpanNamingAdapter} that knows how to handle Apache {@link HttpRequest} and
 * {@link HttpResponse} objects.
 */
public class ApacheHttpClientTagAdapter extends HttpTagAndSpanNamingAdapter<HttpRequest, HttpResponse> {

    @SuppressWarnings("WeakerAccess")
    protected static final ApacheHttpClientTagAdapter DEFAULT_INSTANCE = new ApacheHttpClientTagAdapter();

    /**
     * @return A reusable, thread-safe, singleton instance of this class that can be used by anybody who wants to use
     * this class and does not need any customization.
     */
    @SuppressWarnings("unchecked")
    public static ApacheHttpClientTagAdapter getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public @Nullable String getRequestPath(@Nullable HttpRequest request) {
        if (request == null) {
            return null;
        }

        if (request instanceof HttpRequestWrapper) {
            String path = ((HttpRequestWrapper) request).getURI().getPath();
            // The path shouldn't have a query string on it, but call stripQueryString() just in case.
            return stripQueryString(path);
        }

        // At this point it's not a HttpRequestWrapper. It can be parsed from request.getRequestLine().getUri().
        //      Gross, but doable.
        String requestLine = request.getRequestLine().getUri();

        // Chop out the query string (if any).
        requestLine = stripQueryString(requestLine);

        // If it starts with '/' then there's nothing left for us to do - it's already the path.
        if (requestLine.startsWith("/")) {
            return requestLine;
        }

        // Doesn't start with '/'. We expect it to start with http at this point.
        if (!requestLine.toLowerCase().startsWith("http")) {
            // Didn't start with http. Not sure what to do with this at this point, so return null.
            return null;
        }

        // It starts with http. Chop out the scheme and host/port.
        int schemeColonAndDoubleSlashIndex = requestLine.indexOf("://");
        if (schemeColonAndDoubleSlashIndex < 0) {
            // It didn't have a colon-double-slash after the scheme. Not sure what to do at this point, so return null.
            return null;
        }

        int firstSlashIndexAfterSchemeDoubleSlash = requestLine.indexOf('/', (schemeColonAndDoubleSlashIndex + 3));
        if (firstSlashIndexAfterSchemeDoubleSlash < 0) {
            // No other slashes after the scheme colon-double-slash, so no real path. The path at this point is
            //      effectively "/".
            return "/";
        }

        return requestLine.substring(firstSlashIndexAfterSchemeDoubleSlash);
    }

    @SuppressWarnings("WeakerAccess")
    protected String stripQueryString(String str) {
        int queryIndex = str.indexOf('?');
        return (queryIndex == -1) ? str : str.substring(0, queryIndex);
    }

    @Override
    public @Nullable Integer getResponseHttpStatus(@Nullable HttpResponse response) {
        if (response == null) {
            return null;
        }

        StatusLine statusLine = response.getStatusLine();
        if (statusLine == null) {
            return null;
        }

        return statusLine.getStatusCode();
    }

    @Override
    public @Nullable String getRequestHttpMethod(@Nullable HttpRequest request) {
        if (request == null) {
            return null;
        }
        
        return request.getRequestLine().getMethod();
    }

    @Override
    public @Nullable String getRequestUriPathTemplate(@Nullable HttpRequest request, @Nullable HttpResponse response) {
        // Nothing we can do by default - this needs to be overridden on a per-project basis and given some smarts
        //      based on project-specific knowledge.
        return null;
    }

    @Override
    public @Nullable String getRequestUrl(@Nullable HttpRequest request) {
        if (request == null) {
            return null;
        } 

        String uri = request.getRequestLine().getUri();

        if (request instanceof HttpRequestWrapper && uri.startsWith("/")) {
            HttpRequestWrapper wrapper = (HttpRequestWrapper) request;
            HttpHost target = wrapper.getTarget();
            if (target != null) {
                uri = wrapper.getTarget().toURI() + uri;
            }
        }
        
        return uri;
    }

    @Override
    public @Nullable String getHeaderSingleValue(@Nullable HttpRequest request, @NotNull String headerKey) {
        if (request == null) {
            return null;
        }

        Header matchingHeader = request.getFirstHeader(headerKey);

        if (matchingHeader == null) {
            return null;
        }

        return matchingHeader.getValue();
    }

    @Override
    public @Nullable List<String> getHeaderMultipleValue(@Nullable HttpRequest request, @NotNull String headerKey) {
        if (request == null) {
            return null;
        }

        Header[] matchingHeaders = request.getHeaders(headerKey);

        if (matchingHeaders == null || matchingHeaders.length == 0) {
            return null;
        }

        List<String> returnList = new ArrayList<>(matchingHeaders.length);
        for (Header header : matchingHeaders) {
            returnList.add(header.getValue());
        }

        return returnList;
    }

    @Override
    public @Nullable String getSpanHandlerTagValue(@Nullable HttpRequest request, @Nullable HttpResponse response) {
        return "apache.httpclient";
    }
}
