package com.nike.wingtips.tags;

/**
 * Contains constants for known Zipkin tags. Copied from Zipkin's
 * <a href="https://zipkin.io/public/thrift/v1/zipkinCore.html">Thrift docs</a>.
 *
 * <p>The javadocs on the fields in this class are copied directly from that Thrift documentation. You may need to
 * have some more thorough Zipkin knowledge to be able to fully make sense of them.
 *
 * <p>Additionally, there may be other Zipkin-known-tags from the Zipkin Thrift docs that aren't covered here in this
 * class, or there may be other Zipkin-known-tags that aren't in the Thrift docs at all, but these are the most common
 * you'll likely need to use.
 *
 * @author Nic Munroe
 */
public class KnownZipkinTags {

    // Private constructor so it can't be instantiated.
    private KnownZipkinTags() {}

    /**
     * When an annotation value, this indicates when an error occurred. When a
     * binary annotation key, the value is a human readable message associated
     * with an error.
     *
     * Due to transient errors, an ERROR annotation should not be interpreted
     * as a span failure, even the annotation might explain additional latency.
     * Instrumentation should add the ERROR binary annotation when the operation
     * failed and couldn't be recovered.
     *
     * Here's an example: A span has an ERROR annotation, added when a WIRE_SEND
     * failed. Another WIRE_SEND succeeded, so there's no ERROR binary annotation
     * on the span because the overall operation succeeded.
     *
     * Note that RPC spans often include both client and server hosts: It is
     * possible that only one side perceived the error.
     */
    public static final String ERROR = "error";

    /**
     * The domain portion of the URL or host header. Ex. "mybucket.s3.amazonaws.com"
     *
     * Used to filter by host as opposed to ip address.
     */
    public static final String HTTP_HOST = "http.host";

    /**
     * The HTTP method, or verb, such as "GET" or "POST".
     *
     * Used to filter against an http route.
     */
    public static final String HTTP_METHOD = "http.method";

    /**
     * he absolute http path, without any query parameters. Ex. "/objects/abcd-ff"
     *
     * Used as a filter or to clarify the request path for a given route. For example, the path for
     * a route "/objects/:objectId" could be "/objects/abdc-ff". This does not limit cardinality like
     * HTTP_ROUTE("http.route") can, so is not a good input to a span name.
     *
     * The Zipkin query api only supports equals filters. Dropping query parameters makes the number
     * of distinct URIs less. For example, one can query for the same resource, regardless of signing
     * parameters encoded in the query line. Dropping query parameters also limits the security impact
     * of this tag.
     *
     * Historical note: This was commonly expressed as "http.uri" in zipkin, even though it was most
     */
    public static final String HTTP_PATH = "http.path";

    /**
     * The route which a request matched or "" (empty string) if routing is supported, but there was no
     * match. Ex "/users/{userId}"
     *
     * Unlike HTTP_PATH("http.path"), this value is fixed cardinality, so is a safe input to a span
     * name function or a metrics dimension. Different formats are possible. For example, the following
     * are all valid route templates: "/users" "/users/:userId" "/users/*"
     *
     * Route-based span name generation often uses other tags, such as HTTP_METHOD("http.method") and
     * HTTP_STATUS_CODE("http.status_code"). Route-based names can look like "get /users/{userId}",
     * "post /users", "get not_found" or "get redirected".
     */
    public static final String HTTP_ROUTE = "http.route";

    /**
     * The entire URL, including the scheme, host and query parameters if available. Ex.
     * "https://mybucket.s3.amazonaws.com/objects/abcd-ff?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Algorithm=AWS4-HMAC-SHA256..."
     *
     * Combined with HTTP_METHOD, you can understand the fully-qualified request line.
     *
     * This is optional as it may include private data or be of considerable length.
     */
    public static final String HTTP_URL = "http.url";

    /**
     * The HTTP status code, when not in 2xx range. Ex. "503"
     *
     * Used to filter for error status.
     */
    public static final String HTTP_STATUS_CODE = "http.status_code";

    /**
     * The size of the non-empty HTTP request body, in bytes. Ex. "16384"
     *
     * Large uploads can exceed limits or contribute directly to latency.
     */
    public static final String HTTP_REQUEST_SIZE = "http.request.size";

    /**
     * The size of the non-empty HTTP response body, in bytes. Ex. "16384"
     *
     * Large downloads can exceed limits or contribute directly to latency.
     */
    public static final String HTTP_RESPONSE_SIZE = "http.response.size";

}
