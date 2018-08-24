package com.nike.wingtips.tags;

/**
 * Contains constants for known OpenTracing tags. Copied from OpenTracing's {@code Tags} class
 * <a href="https://github.com/opentracing/opentracing-java/blob/d988630cb2dcb3544f116327464039bf07848293/opentracing-api/src/main/java/io/opentracing/tag/Tags.java">
 *     at this commit
 * </a>.
 *
 * <p>NOTE: Tags in OpenTracing may be typed (string, boolean, number, etc). They are not typed in Wingtips, so
 * all the constants here are strings. See the original OpenTracing {@code Tags} class if you need to know what type of
 * tag a given constant represents.
 *
 * <p>The OpenTracing {@code Tags} class javadocs are as follows:
 *
 * <p>The following span tags are recommended for instrumentors who are trying to capture more
 * semantic information about the spans. Tracers may expose additional features based on these
 * standardized data points. Tag names follow a general structure of namespacing.
 * 
 * @see <a href="https://github.com/opentracing/specification/blob/master/semantic_conventions.md">https://github.com/opentracing/specification/blob/master/semantic_conventions.md</a>
 */
public class KnownOpenTracingTags {

    // Private constructor so it can't be instantiated.
    private KnownOpenTracingTags() {}

    /**
     * A constant for setting the span kind to indicate that it represents a server span.
     */
    public static final String SPAN_KIND_SERVER = "server";

    /**
     * A constant for setting the span kind to indicate that it represents a client span.
     */
    public static final String SPAN_KIND_CLIENT = "client";

    /**
     * A constant for setting the span kind to indicate that it represents a producer span, in a messaging scenario.
     */
    public static final String SPAN_KIND_PRODUCER = "producer";

    /**
     * A constant for setting the span kind to indicate that it represents a consumer span, in a messaging scenario.
     */
    public static final String SPAN_KIND_CONSUMER = "consumer";

    /**
     * The service name for a span, which overrides any default "service name" property defined
     * in a tracer's config. This tag is meant to only be used when a tracer is reporting spans
     * on behalf of another service (for example, a service mesh reporting on behalf of the services
     * it is proxying). This tag does not need to be used when reporting spans for the service the
     * tracer is running in.
     *
     * @see #PEER_SERVICE
     */
    public static final String SERVICE = "service";

    /**
     * HTTP_URL records the url of the incoming request.
     */
    public static final String HTTP_URL = "http.url";

    /**
     * HTTP_STATUS records the http status code of the response.
     */
    public static final String HTTP_STATUS = "http.status_code";

    /**
     * HTTP_METHOD records the http method. Case-insensitive.
     */
    public static final String HTTP_METHOD = "http.method";

    /**
     * PEER_HOST_IPV4 records IPv4 host address of the peer.
     */
    public static final String PEER_HOST_IPV4 = "peer.ipv4";

    /**
     * PEER_HOST_IPV6 records the IPv6 host address of the peer.
     */
    public static final String PEER_HOST_IPV6 = "peer.ipv6";

    /**
     * PEER_SERVICE records the service name of the peer service.
     *
     * @see #SERVICE
     */
    public static final String PEER_SERVICE = "peer.service";

    /**
     * PEER_HOSTNAME records the host name of the peer.
     */
    public static final String PEER_HOSTNAME = "peer.hostname";

    /**
     * PEER_PORT records the port number of the peer.
     */
    public static final String PEER_PORT = "peer.port";

    /**
     * SAMPLING_PRIORITY determines the priority of sampling this Span.
     */
    public static final String SAMPLING_PRIORITY = "sampling.priority";

    /**
     * SPAN_KIND hints at the relationship between spans, e.g. client/server.
     */
    public static final String SPAN_KIND = "span.kind";

    /**
     * COMPONENT is a low-cardinality identifier of the module, library, or package that is instrumented.
     */
    public static final String COMPONENT = "component";

    /**
     * ERROR indicates whether a Span ended in an error state.
     */
    public static final String ERROR = "error";

    /**
     * DB_TYPE indicates the type of Database.
     * For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    public static final String DB_TYPE = "db.type";

    /**
     * DB_INSTANCE indicates the instance name of Database.
     * If the jdbc.url="jdbc:mysql://127.0.0.1:3306/customers", instance name is "customers".
     */
    public static final String DB_INSTANCE = "db.instance";

    /**
     * DB_USER indicates the user name of Database, e.g. "readonly_user" or "reporting_user"
     */
    public static final String DB_USER = "db.user";

    /**
     * DB_STATEMENT records a database statement for the given database type.
     * For db.type="SQL", "SELECT * FROM wuser_table". For db.type="redis", "SET mykey "WuValue".
     */
    public static final String DB_STATEMENT = "db.statement";

    /**
     * MESSAGE_BUS_DESTINATION records an address at which messages can be exchanged.
     * E.g. A Kafka record has an associated "topic name" that can be extracted by the instrumented
     * producer or consumer and stored using this tag.
     */
    public static final String MESSAGE_BUS_DESTINATION = "message_bus.destination";

}
