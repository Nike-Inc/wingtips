package com.nike.wingtips.zipkin;

import com.nike.wingtips.Span;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.zipkin.util.WingtipsToZipkinSpanConverter;
import com.nike.wingtips.zipkin.util.WingtipsToZipkinSpanConverterDefaultImpl;
import com.nike.wingtips.zipkin.util.ZipkinSpanSender;
import com.nike.wingtips.zipkin.util.ZipkinSpanSenderHttpImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import zipkin.Endpoint;

/**
 * <p>
 *     A {@link SpanLifecycleListener} that converts Wingtips {@link Span}s to Zipkin {@link zipkin.Span}s and then sends them
 *     to a Zipkin server in periodic batches, essentially making Wingtips compatible with Zipkin.
 * </p>
 * <p>
 *     For a straightforward no-hassle integration simply use the basic constructor:
 *     {@link #WingtipsToZipkinLifecycleListener(String, String, String)}. If you want more flexibility in order to adjust how
 *     Wingtips spans are converted to Zipkin spans, or adjust how the Zipkin spans are batched up and sent to the Zipkin server, then you
 *     can use the {@link #WingtipsToZipkinLifecycleListener(String, String, WingtipsToZipkinSpanConverter, ZipkinSpanSender)}
 *     constructor.
 * </p>
 * <p>
 *     Note that it's easy to create an adapter that allows you to have a {@code SpanCollector} from the Zipkin Brave libraries function
 *     as a {@link ZipkinSpanSender}. So if you're accustomed to using specific Zipkin {@code SpanCollector}s you can use them with
 *     Wingtips unchanged.
 * </p>
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class WingtipsToZipkinLifecycleListener implements SpanLifecycleListener {

    private final Logger zipkinConversionOrReportingErrorLogger = LoggerFactory.getLogger("ZIPKIN_SPAN_CONVERSION_OR_HANDLING_ERROR");

    protected final String serviceName;
    protected final String localComponentNamespace;
    protected final Endpoint zipkinEndpoint;
    protected final WingtipsToZipkinSpanConverter zipkinSpanConverter;
    protected final ZipkinSpanSender zipkinSpanSender;

    protected final AtomicLong spanHandlingErrorCounter = new AtomicLong(0);
    protected long lastSpanHandlingErrorLogTimeEpochMillis = 0;
    protected static final long MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(60);

    /**
     * Kitchen-sink constructor that lets you set all the options.
     *
     * @param serviceName The name of this service. This is used to build the Zipkin {@link Endpoint} that will be used for client/server/local
     *                    Zipkin annotations when sending spans to Zipkin.
     * @param localComponentNamespace The {@link zipkin.Constants#LOCAL_COMPONENT} namespace that should be used when creating certain Zipkin
     *                                annotations when the Wingtips span's {@link Span#getSpanPurpose()} is
     *                                {@link com.nike.wingtips.Span.SpanPurpose#LOCAL_ONLY}. See the {@link zipkin.Constants#LOCAL_COMPONENT}
     *                                javadocs for more information on what this is and how it's used by the Zipkin server, so you know
     *                                what value you should send.
     * @param zipkinSpanConverter The {@link WingtipsToZipkinSpanConverter} that should be used to convert Wingtips spans to Zipkin spans.
     * @param zipkinSpanSender The {@link ZipkinSpanSender} for collecting and sending Zipkin spans to the Zipkin server.
     */
    public WingtipsToZipkinLifecycleListener(String serviceName, String localComponentNamespace, WingtipsToZipkinSpanConverter zipkinSpanConverter,
                                             ZipkinSpanSender zipkinSpanSender) {
        this.serviceName = serviceName;
        this.localComponentNamespace = localComponentNamespace;
        this.zipkinEndpoint = Endpoint.builder().serviceName(serviceName).build();
        this.zipkinSpanConverter = zipkinSpanConverter;
        this.zipkinSpanSender = zipkinSpanSender;
    }

    /**
     * Convenience constructor that uses {@link WingtipsToZipkinSpanConverterDefaultImpl} and {@link ZipkinSpanSenderHttpImpl} as the
     * implementations for {@link #zipkinSpanConverter} and {@link #zipkinSpanSender}.
     *
     * @param serviceName The name of this service. This is used to build the Zipkin {@link Endpoint} that will be used for client/server/local
     *                    Zipkin annotations when sending spans to Zipkin.
     * @param localComponentNamespace The {@link zipkin.Constants#LOCAL_COMPONENT} namespace that should be used when creating certain Zipkin
     *                                annotations when the Wingtips span's {@link Span#getSpanPurpose()} is
     *                                {@link com.nike.wingtips.Span.SpanPurpose#LOCAL_ONLY}. See the {@link zipkin.Constants#LOCAL_COMPONENT}
     *                                javadocs for more information on what this is and how it's used by the Zipkin server, so you know
     *                                what value you should send.
     * @param postZipkinSpansBaseUrl The base URL of the Zipkin server. This should include the scheme, host, and port (if non-standard for the scheme).
     *                               e.g. {@code http://localhost:9411}, or {@code https://zipkinserver.doesnotexist.com/}
     */
    public WingtipsToZipkinLifecycleListener(String serviceName, String localComponentNamespace, String postZipkinSpansBaseUrl) {
        this(serviceName,
             localComponentNamespace,
             new WingtipsToZipkinSpanConverterDefaultImpl(),
             new ZipkinSpanSenderHttpImpl(postZipkinSpansBaseUrl, true)
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
            zipkin.Span zipkinSpan = zipkinSpanConverter.convertWingtipsSpanToZipkinSpan(span, zipkinEndpoint, localComponentNamespace);
            zipkinSpanSender.handleSpan(zipkinSpan);
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
                    "There have been {} spans that were not zipkin compatible, or that experienced an error during span handling. Latest example: "
                    + "wingtips_span_with_error=\"{}\", conversion_or_handling_error=\"{}\"",
                    currentBadSpanCount, span.toKeyValueString(), ex.toString());
            }
        }
    }
}