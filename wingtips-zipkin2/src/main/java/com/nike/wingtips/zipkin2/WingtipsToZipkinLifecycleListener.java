package com.nike.wingtips.zipkin2;

import com.nike.wingtips.Span;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverterDefaultImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import zipkin2.Endpoint;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * A {@link SpanLifecycleListener} that converts Wingtips {@link Span}s to Zipkin {@link zipkin2.Span}s and then
 * sends them to a Zipkin server using a Zipkin {@link Reporter}, essentially making Wingtips compatible with Zipkin.
 *
 * <p>For a straightforward no-hassle integration simply use the basic constructor: {@link
 * #WingtipsToZipkinLifecycleListener(String, String)}. This assumes you're sending spans to a standard Zipkin
 * v2-API-compatible server (Zipkin Server 1.31+) over HTTP and don't need any customization of the HTTP calls. If you
 * want more flexibility in order to adjust how Wingtips spans are converted to Zipkin spans, or adjust how the Zipkin
 * spans are batched up and sent to the Zipkin server, then you can use the {@link
 * #WingtipsToZipkinLifecycleListener(String, WingtipsToZipkinSpanConverter, Reporter)} constructor.
 *
 * <p>Note that Zipkin has one main {@link Reporter} ({@link AsyncReporter}) but a wide array of {@link
 * zipkin2.reporter.Sender}s (used by {@link AsyncReporter} for putting the encoded spans onto a transport like HTTP)
 * for you to choose from. By default the simple {@link
 * WingtipsToZipkinLifecycleListener#WingtipsToZipkinLifecycleListener(String, String)} constructor for this class will
 * use the lightweight {@link URLConnectionSender} to send Zipkin spans over HTTP to a Zipkin server, however you can
 * choose from any of the Zipkin {@link zipkin2.reporter.Sender}s by using the {@link
 * WingtipsToZipkinLifecycleListener#WingtipsToZipkinLifecycleListener(String, WingtipsToZipkinSpanConverter, Reporter)}
 * constructor and passing an {@link AsyncReporter} that wraps whatever {@link Sender} you want. i.e.:
 *
 * <pre>
 *  Sender zipkinSpanSenderToUse = ...;
 *  Reporter&lt;zipkin2.Span> zipkinReporterToUse = AsyncReporter
 *      .builder(zipkinSpanSenderToUse)
 *      // Extra Reporter customization goes here (if desired) using the AsyncReporter.Builder.
 *      .build();
 *  WingtipsToZipkinLifecycleListener w2zListener = new WingtipsToZipkinLifecycleListener(serviceName, spanConverter, zipkinReporterToUse);
 * </pre>
 *
 * For more information on how to use Zipkin {@link Reporter}s and {@link Sender}s see the
 * <a href="https://github.com/openzipkin/zipkin-reporter-java">Official Zipkin Reporter Docs</a>. For a list of
 * available {@link Sender}s to choose from do a
 * <a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.reporter2%22">Maven Search for io.zipkin.reporter2 GroupId</a>.
 * In particular note that the {@code OkHttpSender} is also HTTP, but allows you to customize the calls (for example
 * if you need to pass auth headers to get through a proxy in front of your Zipkin server).
 *
 * <p>If your Zipkin server only supports the Zipkin v1 Span format instead of the Zipkin v2 format that is assumed by
 * default in the various {@link Sender}s, you simply need to specify a {@link Sender} configured to use Zipkin v1 endpoint,
 * and tell the {@link Reporter} to encode in v1 format. See the "Legacy Encoding" section of the official Zipkin docs
 * <a href="https://github.com/openzipkin/zipkin-reporter-java#legacy-encoding">here</a> for details, but a simple example
 * using the basic {@link URLConnectionSender} would look like:
 *
 * <pre>
 *  // Use the Zipkin v1 endpoint when creating the Sender.
 *  Sender zipkinV1Sender = URLConnectionSender.create(postZipkinSpansBaseUrl + "/api/v1/spans");
 *  // Use the Zipkin v1 span encoder when creating the Reporter.
 *  Reporter<zipkin2.Span> zipkinV1Reporter = AsyncReporter.builder(zipkinV1Sender).build(SpanBytesEncoder.JSON_V1);
 *  WingtipsToZipkinLifecycleListener w2zListener = new WingtipsToZipkinLifecycleListener(serviceName, spanConverter, zipkinV1Reporter);
 * </pre>
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class WingtipsToZipkinLifecycleListener implements SpanLifecycleListener {

    private final Logger zipkinConversionOrReportingErrorLogger = LoggerFactory.getLogger("ZIPKIN_SPAN_CONVERSION_OR_HANDLING_ERROR");

    protected final String serviceName;
    protected final Endpoint zipkinEndpoint;
    protected final WingtipsToZipkinSpanConverter zipkinSpanConverter;
    protected final Reporter<zipkin2.Span> zipkinSpanReporter;

    protected final AtomicLong spanHandlingErrorCounter = new AtomicLong(0);
    protected long lastSpanHandlingErrorLogTimeEpochMillis = 0;
    protected static final long MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(60);

    /**
     * Kitchen-sink constructor that lets you set all the options. If you're in the simple use case of wanting to
     * send spans to Zipkin over HTTP with no customizations you can use the basic {@link
     * WingtipsToZipkinLifecycleListener#WingtipsToZipkinLifecycleListener(String, String)} constructor instead.
     *
     * @param serviceName The name of this service. This is used to build the Zipkin {@link Endpoint}, which tells
     * Zipkin which service generated the spans we're going to send it.
     * @param zipkinSpanConverter The {@link WingtipsToZipkinSpanConverter} that should be used to convert Wingtips
     * spans to Zipkin spans.
     * @param zipkinSpanReporter The Zipkin {@link Reporter} for collecting and sending Zipkin spans to the Zipkin
     * server.
     */
    public WingtipsToZipkinLifecycleListener(String serviceName, WingtipsToZipkinSpanConverter zipkinSpanConverter, Reporter<zipkin2.Span> zipkinSpanReporter) {
        this.serviceName = serviceName;
        // TODO: Maybe try and get IP address for the Zipkin Endpoint? See https://github.com/Nike-Inc/wingtips/pull/70#pullrequestreview-136998397
        //      for the suggestion, and I think this is the impl: https://github.com/openzipkin/brave/blob/af055a61330a10afa9b6fa4f05f7d33a3b3a7296/brave/src/main/java/brave/internal/Platform.java#L60-L83
        this.zipkinEndpoint = Endpoint.newBuilder().serviceName(serviceName).build();
        this.zipkinSpanConverter = zipkinSpanConverter;
        this.zipkinSpanReporter = zipkinSpanReporter;
    }

    /**
     * Convenience constructor that uses {@link WingtipsToZipkinSpanConverterDefaultImpl} for converting Wingtips spans
     * to Zipkin spans, {@link AsyncReporter} for the Zipkin reporter, and {@link URLConnectionSender} as the underlying
     * {@link Sender} used by the {@link AsyncReporter}. If you need more flexibility in the span converter or
     * reporter/sender, then use the {@link
     * WingtipsToZipkinLifecycleListener#WingtipsToZipkinLifecycleListener(String, WingtipsToZipkinSpanConverter, Reporter)}
     * constructor instead.
     *
     * @param serviceName The name of this service. This is used to build the Zipkin {@link Endpoint}, which tells
     * Zipkin which service generated the spans we're going to send it.
     * @param postZipkinSpansBaseUrl The base URL of the Zipkin server. This should include the scheme, host, and port
     * (if non-standard for the scheme). e.g. {@code http://localhost:9411}, or
     * {@code https://zipkinserver.doesnotexist.com/}. This assumes you want to send spans to Zipkin over HTTP - if
     * you want to use something else then you'll need to specify your own reporter/sender using the {@link
     * WingtipsToZipkinLifecycleListener#WingtipsToZipkinLifecycleListener(String, WingtipsToZipkinSpanConverter, Reporter)}
     * constructor instead.
     */
    public WingtipsToZipkinLifecycleListener(String serviceName, String postZipkinSpansBaseUrl) {
        this(serviceName,
             new WingtipsToZipkinSpanConverterDefaultImpl(),
             AsyncReporter.create(
                 URLConnectionSender.create(
                     postZipkinSpansBaseUrl + (postZipkinSpansBaseUrl.endsWith("/") ? "" : "/") + "api/v2/spans"
                 )
             )
        );
    }

    @Override
    public void spanStarted(Span span) {
        // Do nothing
    }

    @Override
    public void spanSampled(Span span) {
        // Do nothing
    }

    @Override
    public void spanCompleted(Span span) {
        try {
            zipkin2.Span zipkinSpan = zipkinSpanConverter.convertWingtipsSpanToZipkinSpan(span, zipkinEndpoint);
            zipkinSpanReporter.report(zipkinSpan);
        }
        catch(Throwable ex) {
            long currentBadSpanCount = spanHandlingErrorCounter.incrementAndGet();

            // Only log once every MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS time interval to prevent log spam from a malicious (or broken) caller.
            long currentTimeMillis = System.currentTimeMillis();
            long timeSinceLastLogMsgMillis = currentTimeMillis - lastSpanHandlingErrorLogTimeEpochMillis;
            if (timeSinceLastLogMsgMillis >= MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS) {
                // We're not synchronizing the read and write to lastSpanHandlingErrorLogTimeEpochMillis, and that's ok. If we get a few extra
                //      log messages due to a race condition it's not the end of the world - we're still satisfying the goal of not allowing a
                //      malicious caller to endlessly spam the logs.
                lastSpanHandlingErrorLogTimeEpochMillis = currentTimeMillis;

                zipkinConversionOrReportingErrorLogger.warn(
                    "There have been {} spans that were not Zipkin compatible, or that experienced an error during span handling. Latest example: "
                    + "wingtips_span_with_error=\"{}\", conversion_or_handling_error=\"{}\"",
                    currentBadSpanCount, span.toKeyValueString(), ex.toString());
            }
        }
    }
}