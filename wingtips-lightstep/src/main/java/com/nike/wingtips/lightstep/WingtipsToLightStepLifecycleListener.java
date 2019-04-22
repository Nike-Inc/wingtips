package com.nike.wingtips.lightstep;

import com.nike.wingtips.Span;
import com.nike.wingtips.TraceAndSpanIdGenerator;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.SpanBuilder;
import com.lightstep.tracer.shared.SpanContext;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

/**
 * A {@link SpanLifecycleListener} that converts Wingtips {@link Span}s to [LightStep implementation of] an OpenTracing
 * {@link io.opentracing.Span}, and sends that data to the LightStep Satellites via
 * the {@link JRETracer}.
 *
 * We're adapting some of the prior work built in the Wingtips Zipkin2 plugin to handle conversion of span/trace/parent
 * IDs as well as frequency gates for exception logging.
 *
 * Required options used in the constructor are the LightStep access token (generated in project settings within
 * LightStep), service name (which will be assigned to all spans), Satellite URL and Satellite Port, which should both
 * reflect the address for the load balancer in front of the LightStep Satellites.
 *
 * @author parker@lightstep.com
 */

public class WingtipsToLightStepLifecycleListener implements SpanLifecycleListener {

    // we borrowed the logging and exception log rate limiting from the Zipkin plugin.
    private final Logger lightStepToWingtipsLogger =
        LoggerFactory.getLogger("LIGHTSTEP_SPAN_CONVERSION_OR_HANDLING_ERROR");

    private static final String SANITIZED_ID_LOG_MSG = "Detected invalid ID format. orig_id={}, sanitized_id={}";

    private final AtomicLong spanHandlingErrorCounter = new AtomicLong(0);
    private long lastSpanHandlingErrorLogTimeEpochMillis = 0;
    private static final long MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(60);

    protected final JRETracer tracer;

    // Basic constructor which requires values to configure tracer and point span traffic from the transport library
    // to the LightStep Satellites.
    public WingtipsToLightStepLifecycleListener(
        @NotNull String serviceName,
        @NotNull String accessToken,
        @NotNull String satelliteUrl,
        int satellitePort
    ) {
        this(buildJreTracerFromOptions(serviceName, accessToken, satelliteUrl, satellitePort));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WingtipsToLightStepLifecycleListener(@NotNull JRETracer tracer) {
        requireNonNull(tracer, "tracer cannot be null.");
        this.tracer = tracer;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static @NotNull JRETracer buildJreTracerFromOptions(
        @NotNull String serviceName,
        @NotNull String accessToken,
        @NotNull String satelliteUrl,
        int satellitePort
    ) {
        requireNonNull(serviceName, "serviceName cannot be null.");
        requireNonNull(accessToken, "accessToken cannot be null.");
        requireNonNull(satelliteUrl, "satelliteUrl cannot be null.");

        try {
            return new JRETracer(
                new com.lightstep.tracer.shared.Options.OptionsBuilder()
                    .withAccessToken(accessToken)
                    .withComponentName(serviceName)
                    .withCollectorHost(satelliteUrl)
                    .withCollectorPort(satellitePort)
                    .withVerbosity(4)
                    .build()
            );
        } catch (Exception ex) {
            throw new RuntimeException("There was an error initializing the LightStep tracer.", ex);
        }
    }

    @Override
    public void spanSampled(Span wingtipsSpan) {
        // Do nothing
    }

    @Override
    public void spanStarted(Span wingtipsSpan) {
        // Do nothing
    }

    @Override
    public void spanCompleted(Span wingtipsSpan) {
        try {
            String operationName = wingtipsSpan.getSpanName();
            long startTimeMicros = wingtipsSpan.getSpanStartTimeEpochMicros();

            // Given we should only be in this method on span completion, we are not going to wrap this conversion in a
            // try/catch. duration should be set on the Wingtips span.
            long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());
            long stopTimeMicros = startTimeMicros + durationMicros;

            // Sanitize the wingtips trace/span/parent IDs if necessary. This guarantees we can convert them to
            //      longs as required by LightStep.
            String wtSanitizedSpanId = sanitizeIdIfNecessary(wingtipsSpan.getSpanId(), false);
            String wtSanitizedTraceId = sanitizeIdIfNecessary(wingtipsSpan.getTraceId(), true);
            String wtSanitizedParentId = sanitizeIdIfNecessary(wingtipsSpan.getParentSpanId(), false);

            // LightStep requires Ids to be longs.
            long lsSpanId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(wtSanitizedSpanId);
            long lsTraceId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(wtSanitizedTraceId);

            // Handle the common SpanBuilder settings.
            SpanBuilder lsSpanBuilder = (SpanBuilder) (
                tracer.buildSpan(operationName)
                      .withStartTimestamp(wingtipsSpan.getSpanStartTimeEpochMicros())
                      .ignoreActiveSpan()
                      .withTag("lightstep.trace_id", lsTraceId)
                      .withTag("lightstep.span_id", lsSpanId)
                      .withTag("wingtips.span_id", wingtipsSpan.getSpanId())
                      .withTag("wingtips.trace_id", wingtipsSpan.getTraceId())
            );

            // Handle the parent ID / parent context SpanBuilder settings.
            if (wingtipsSpan.getParentSpanId() != null) {
                long lsParentId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(wtSanitizedParentId);

                SpanContext lsSpanContext = new SpanContext(lsTraceId, lsParentId);

                lsSpanBuilder = (SpanBuilder)(
                    lsSpanBuilder.asChildOf(lsSpanContext)
                                 .withTag("lightstep.parent_id", lsParentId)
                                 .withTag("wingtips.parent_id", wingtipsSpan.getParentSpanId())
                );
            }
            else {
                lsSpanBuilder = (SpanBuilder)(
                    lsSpanBuilder.withTag("lightstep.parent_id", "null")
                                 .withTag("wingtips.parent_id", "null")
                );
            }

            // Start the OT span and set logs and tags from the wingtips span.
            io.opentracing.Span lsSpan = lsSpanBuilder.start();

            for (Span.TimestampedAnnotation wingtipsAnnotation : wingtipsSpan.getTimestampedAnnotations()) {
                lsSpan.log(wingtipsAnnotation.getTimestampEpochMicros(), wingtipsAnnotation.getValue());
            }

            lsSpan.setTag("span.type", wingtipsSpan.getSpanPurpose().name());

            for (Map.Entry<String, String> wtTag : wingtipsSpan.getTags().entrySet()) {
                lsSpan.setTag(wtTag.getKey(), wtTag.getValue());
            }

            // Add some custom boolean tags if any of the IDs had to be sanitized. The raw unsanitized ID will be
            //      available via the wingtips.*_id tags.
            if (!wtSanitizedSpanId.equals(wingtipsSpan.getSpanId())) {
                lsSpan.setTag("wingtips.span_id.invalid", true);
            }
            if (!wtSanitizedTraceId.equals(wingtipsSpan.getTraceId())) {
                lsSpan.setTag("wingtips.trace_id.invalid", true);
            }
            if (wtSanitizedParentId != null && !wtSanitizedParentId.equals(wingtipsSpan.getParentSpanId())) {
                lsSpan.setTag("wingtips.parent_id.invalid", true);
            }

            // on finish, the tracer library initialized on the creation of this listener will cache and transport the span
            // data to the LightStep Satellite.
            lsSpan.finish(stopTimeMicros);
        } catch (Exception ex) {
            long currentBadSpanCount = spanHandlingErrorCounter.incrementAndGet();
            // Adopted from WingtipsToZipkinLifecycleListener from Wingtips-Zipkin2 plugin.
            // Only log once every MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS time interval to prevent log spam from a
            // malicious (or broken) caller.
            long currentTimeMillis = System.currentTimeMillis();
            long timeSinceLastLogMsgMillis = currentTimeMillis - lastSpanHandlingErrorLogTimeEpochMillis;

            if (timeSinceLastLogMsgMillis >= MIN_SPAN_HANDLING_ERROR_LOG_INTERVAL_MILLIS) {
                // We're not synchronizing the read and write to lastSpanHandlingErrorLogTimeEpochMillis, and that's ok.
                // If we get a few extra log messages due to a race condition it's not the end of the world - we're
                // still satisfying the goal of not allowing a malicious caller to endlessly spam the logs.
                lastSpanHandlingErrorLogTimeEpochMillis = currentTimeMillis;

                lightStepToWingtipsLogger.warn(
                        "There have been {} spans that were not LightStep compatible, or that experienced an error "
                        + "during span handling. Latest example: "
                        + "wingtips_span_with_error=\"{}\", conversion_or_handling_error=\"{}\"",
                        currentBadSpanCount, wingtipsSpan.toKeyValueString(), ex.toString()
                );
            }
        }
    }
    protected String sanitizeIdIfNecessary(final String originalId, final boolean allow128Bit) {

        if (originalId == null) {
            return null;
        }

        if (isAllowedNumChars(originalId, allow128Bit)) {
            if (isLowerHex(originalId)) {
                // Already lowerhex with correct number of chars, no modifications needed.
                return originalId;
            }
            else if (isHex(originalId, true)) {
                // It wasn't lowerhex, but it is hex and it is the correct number of chars.
                //      We can trivially convert to valid lowerhex by lowercasing the ID.
                String sanitizedId = originalId.toLowerCase();
                lightStepToWingtipsLogger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
                return sanitizedId;
            }
        }

        // If the originalId can be parsed as a long, then its sanitized ID is the lowerhex representation of that long.
        Long originalIdAsRawLong = attemptToConvertToLong(originalId);
        if (originalIdAsRawLong != null) {
            String sanitizedId = TraceAndSpanIdGenerator.longToUnsignedLowerHexString(originalIdAsRawLong);
            lightStepToWingtipsLogger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
            return sanitizedId;
        }

        // If the originalId can be parsed as a UUID and is allowed to be 128 bit,
        //      then its sanitized ID is that UUID with the dashes ripped out and forced lowercase.
        if (allow128Bit && attemptToConvertToUuid(originalId) != null) {
            String sanitizedId = originalId.replace("-", "").toLowerCase();
            lightStepToWingtipsLogger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
            return sanitizedId;
        }

        // No convenient/sensible conversion to a valid lowerhex ID was found.
        //      Do a SHA256 hash of the original ID to get a (deterministic) valid sanitized lowerhex ID that can be
        //      converted to a long, but only take the number of characters we're allowed to take. Truncation
        //      of a SHA digest like this is specifically allowed by the SHA algorithm - see Section 7
        //      ("TRUNCATION OF A MESSAGE DIGEST") here:
        //      https://csrc.nist.gov/csrc/media/publications/fips/180/4/final/documents/fips180-4-draft-aug2014.pdf
        int allowedNumChars = allow128Bit ? 32 : 16;
        String sanitizedId = DigestUtils.sha256Hex(originalId).toLowerCase().substring(0, allowedNumChars);
        lightStepToWingtipsLogger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
        return sanitizedId;
    }

    private boolean isLowerHex(String id) {
        return isHex(id, false);
    }

    /**
     *
     * @param id The ID to check for hexadecimal conformity.
     * @param allowUppercase Pass true to allow uppercase A-F letters, false to force lowercase-hexadecimal check
     * (only a-f letters allowed).
     * @return true if the given id is hexadecimal, false if there are any characters that are not hexadecimal, with
     * the {@code allowUppercase} parameter determining whether uppercase hex characters are allowed.
     */
    private boolean isHex(String id, boolean allowUppercase) {
        for (int i = 0, length = id.length(); i < length; i++) {
            char c = id.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                // Not 0-9, and not a-f. So it's not lowerhex. If we don't allow uppercase then we can return false.
                if (!allowUppercase) {
                    return false;
                }
                else if (c < 'A' || c > 'F') {
                    // Uppercase is allowed but it's not A-F either, so we still have to return false.
                    return false;
                }

                // If we reach here inside this if-block, then it's an uppercase A-F and allowUppercase is true, so
                //      do nothing and move onto the next character.
            }
        }

        return true;
    }

    private boolean isAllowedNumChars(final String id, final boolean allow128Bit) {
        if (allow128Bit) {
            return id.length() <= 16 || id.length() == 32;
        } else {
            return id.length() <= 16;
        }
    }

    private Long attemptToConvertToLong(final String id) {
        try {
            return Long.valueOf(id);
        } catch (final NumberFormatException nfe) {
            return null;
        }
    }

    private UUID attemptToConvertToUuid(String originalId) {
        try {
            return UUID.fromString(originalId);
        }
        catch(Exception t) {
            return null;
        }
    }
}
