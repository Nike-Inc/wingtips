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

@SuppressWarnings("WeakerAccess")
public class WingtipsToLightStepLifecycleListener implements SpanLifecycleListener {

    // we borrowed the logging and exception log rate limiting from the Zipkin plugin.
    private final Logger lightStepToWingtipsLogger =
        LoggerFactory.getLogger("LIGHTSTEP_SPAN_CONVERSION_OR_HANDLING_ERROR");

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

    public WingtipsToLightStepLifecycleListener(@NotNull JRETracer tracer) {
        requireNonNull(tracer, "tracer cannot be null.");
        this.tracer = tracer;
    }

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
                    .withVerbosity(1)
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

    protected boolean shouldReportCompletedSpan(Span span) {
        // We only want to send the span if it was sampled.
        return span.isSampleable();
    }

    @Override
    public void spanCompleted(Span wingtipsSpan) {
        if (!shouldReportCompletedSpan(wingtipsSpan)) {
            return;
        }

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

            // Handle the common SpanBuilder settings.
            SpanBuilder lsSpanBuilder = (SpanBuilder) (
                tracer.buildSpan(operationName)
                      .withStartTimestamp(wingtipsSpan.getSpanStartTimeEpochMicros())
                      .ignoreActiveSpan()
                      .withTag("wingtips.span_id", wingtipsSpan.getSpanId())
                      .withTag("wingtips.trace_id", wingtipsSpan.getTraceId())
                      .withTag("wingtips.parent_id", String.valueOf(wingtipsSpan.getParentSpanId()))
                      .withTag("span.type", wingtipsSpan.getSpanPurpose().name())
            );

            // Force the LightStep span to have a Trace ID and Span ID matching the Wingtips span.
            //      NOTE: LightStep requires Ids to be longs, so we convert the sanitized wingtips trace/span IDs.
            long lsSpanId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(wtSanitizedSpanId);
            long lsTraceId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(wtSanitizedTraceId);
            lsSpanBuilder.withTraceIdAndSpanId(lsTraceId, lsSpanId);

            // Handle the parent ID / parent context SpanBuilder settings.
            if (wingtipsSpan.getParentSpanId() != null) {
                long lsParentId = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(wtSanitizedParentId);

                SpanContext lsSpanContext = new SpanContext(lsTraceId, lsParentId);

                lsSpanBuilder = (SpanBuilder)(lsSpanBuilder.asChildOf(lsSpanContext));
            }

            // Start the OT span and set logs and tags from the wingtips span.
            io.opentracing.Span lsSpan = lsSpanBuilder.start();

            for (Span.TimestampedAnnotation wingtipsAnnotation : wingtipsSpan.getTimestampedAnnotations()) {
                lsSpan.log(wingtipsAnnotation.getTimestampEpochMicros(), wingtipsAnnotation.getValue());
            }

            for (Map.Entry<String, String> wtTag : wingtipsSpan.getTags().entrySet()) {
                lsSpan.setTag(wtTag.getKey(), wtTag.getValue());
            }

            // Add some custom boolean tags if any of the IDs had to be sanitized. The raw unsanitized ID will be
            //      available via the wingtips.*_id tags.
            if (!wtSanitizedSpanId.equals(wingtipsSpan.getSpanId())) {
                lsSpan.setTag("wingtips.span_id.invalid", true);
                wingtipsSpan.putTag("sanitized_span_id", wtSanitizedSpanId);
            }
            if (!wtSanitizedTraceId.equals(wingtipsSpan.getTraceId())) {
                lsSpan.setTag("wingtips.trace_id.invalid", true);
                wingtipsSpan.putTag("sanitized_trace_id", wtSanitizedTraceId);
            }
            if (wtSanitizedParentId != null && !wtSanitizedParentId.equals(wingtipsSpan.getParentSpanId())) {
                lsSpan.setTag("wingtips.parent_id.invalid", true);
                wingtipsSpan.putTag("sanitized_parent_id", wtSanitizedParentId);
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

    // TODO: The sanitization logic is a copy/paste from WingtipsToZipkinSpanConverterDefaultImpl. We should figure out
    //       a way to share the code. We could move it to wingtips-core, but this uses DigestUtils, and we don't want
    //       to add that dependency to wingtips-core.
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
                return originalId.toLowerCase();
            }
        }

        // If the originalId can be parsed as a long, then its sanitized ID is the lowerhex representation of that long.
        Long originalIdAsRawLong = attemptToConvertToLong(originalId);
        if (originalIdAsRawLong != null) {
            return TraceAndSpanIdGenerator.longToUnsignedLowerHexString(originalIdAsRawLong);
        }

        // If the originalId can be parsed as a UUID and is allowed to be 128 bit,
        //      then its sanitized ID is that UUID with the dashes ripped out and forced lowercase.
        if (allow128Bit) {
            String sanitizedId = attemptToSanitizeAsUuid(originalId);
            if (sanitizedId != null) {
                return sanitizedId;
            }
        }

        // No convenient/sensible conversion to a valid lowerhex ID was found.
        //      Do a SHA256 hash of the original ID to get a (deterministic) valid sanitized lowerhex ID that can be
        //      converted to a long, but only take the number of characters we're allowed to take. Truncation
        //      of a SHA digest like this is specifically allowed by the SHA algorithm - see Section 7
        //      ("TRUNCATION OF A MESSAGE DIGEST") here:
        //      https://csrc.nist.gov/csrc/media/publications/fips/180/4/final/documents/fips180-4-draft-aug2014.pdf
        int allowedNumChars = allow128Bit ? 32 : 16;
        return DigestUtils.sha256Hex(originalId).toLowerCase().substring(0, allowedNumChars);
    }

    protected boolean isLowerHex(String id) {
        return isHex(id, false);
    }

    /**
     * Copied from {@link zipkin2.Span#validateHex(String)} and slightly modified.
     *
     * @param id The ID to check for hexadecimal conformity.
     * @param allowUppercase Pass true to allow uppercase A-F letters, false to force lowercase-hexadecimal check
     * (only a-f letters allowed).
     *
     * @return true if the given id is hexadecimal, false if there are any characters that are not hexadecimal, with
     * the {@code allowUppercase} parameter determining whether uppercase hex characters are allowed.
     */
    protected boolean isHex(String id, boolean allowUppercase) {
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

    protected boolean isAllowedNumChars(final String id, final boolean allow128Bit) {
        if (allow128Bit) {
            return id.length() <= 16 || id.length() == 32;
        }
        else {
            return id.length() <= 16;
        }
    }

    private static final char[] MAX_LONG_AS_CHAR_ARRY = String.valueOf(Long.MAX_VALUE).toCharArray();
    private static final int MIN_OR_MAX_LONG_NUM_DIGITS = MAX_LONG_AS_CHAR_ARRY.length;
    private static final char[] ABS_MIN_LONG_AS_CHAR_ARRY = String.valueOf(Long.MIN_VALUE).substring(1).toCharArray();
    static {
        // Sanity check that MAX_LONG_AS_CHAR_ARRY and ABS_MIN_LONG_AS_CHAR_ARRY have the same number of chars.
        assert MAX_LONG_AS_CHAR_ARRY.length == ABS_MIN_LONG_AS_CHAR_ARRY.length;
    }
    protected Long attemptToConvertToLong(final String id) {
        if (id == null) {
            return null;
        }

        // Only try if all chars in the ID are digits (the first char is allowed to be a dash to indicate negative).
        int numDigits = 0;
        boolean firstCharIsDash = false;
        for (int i = 0; i < id.length(); i++) {
            char nextChar = id.charAt(i);
            boolean isDigit = (nextChar >= '0' && nextChar <= '9');
            if (isDigit) {
                numDigits++;
            }
            else {
                // The first char is allowed to be a dash.
                if (i == 0 && nextChar == '-') {
                    // This is allowed.
                    firstCharIsDash = true;
                }
                else {
                    // Not a digit, and not a first-char-dash. So id cannot be a long. Return null.
                    return null;
                }
            }
        }

        // All chars are digits (or negative sign followed by digits). Next we need to make sure they are in the
        //      valid range of a java long.
        if (!isWithinRangeOfJavaLongMinAndMax(id, numDigits, firstCharIsDash)) {
            // Too many digits, or the value was too high. Can't be converted to java long, so return null.
            return null;
        }

        try {
            // This *should* be convertible to a java long at this point.
            return Long.parseLong(id);
        }
        catch (final NumberFormatException nfe) {
            // This should never happen, but if it does, return null as it can't be converted to a long.
            lightStepToWingtipsLogger.warn(
                "Found digits-based-ID that reached Long.parseLong(id) and failed. "
                + "This should never happen - please report this to the Wingtips maintainers. "
                + "invalid_id={}, NumberFormatException={}",
                id, nfe.toString()
            );
            return null;
        }
    }

    protected boolean isWithinRangeOfJavaLongMinAndMax(String longAsString, int numDigits, boolean firstCharIsDash) {
        if (numDigits < MIN_OR_MAX_LONG_NUM_DIGITS) {
            // Fewer number of digits than max java long, so it is in the valid min/max java long range.
            return true;
        }

        if (numDigits > MIN_OR_MAX_LONG_NUM_DIGITS) {
            // Too many digits, so it is outside the valid min/max java long range.
            return false;
        }

        // At this point, we know the ID has the same number of digits as the max java long value. The ID may or may
        //      not be within the range of a java long. We could use a BigInteger to compare, but that's too slow.
        //      So we'll root through digit by digit.

        // Adjust the string we're checking to get rid of the negative sign (dash) if necessary.
        String absLongAsString = (firstCharIsDash) ? longAsString.substring(1) : longAsString;

        // Choose the correct min/max value to compare against.
        char[] comparisonValue = (firstCharIsDash) ? ABS_MIN_LONG_AS_CHAR_ARRY : MAX_LONG_AS_CHAR_ARRY;

        for (int i = 0; i < comparisonValue.length; i++) {
            char nextCharAtIndex = absLongAsString.charAt(i);
            char maxLongCharAtIndex = comparisonValue[i];
            if (nextCharAtIndex > maxLongCharAtIndex) {
                // Up to this index, the values were equal, but *at* this index the value is greater than max java long,
                //      so it's outside the java long range.
                return false;
            }
            else if (nextCharAtIndex < maxLongCharAtIndex) {
                // Up to this index, the values were equal, but *at* this index the value is less than max java long,
                //      so it's within the java long range.
                return true;
            }

            // If neither of the comparisons above were triggered, then this char matches the max long char. Move on
            //      to the next char.
        }

        // If we reach here, then longAsString has the same number of digits as max java long, and all digits were
        //      equal. So this is exactly min or max java long, and therefore within range.
        return true;
    }

    protected String attemptToSanitizeAsUuid(String originalId) {
        if (originalId == null) {
            return null;
        }

        if (originalId.length() == 36) {
            // 36 chars - might be a UUID. Rip out the dashes and check to see if it's a valid 128 bit ID.
            String noDashesAndLowercase = stripDashesAndConvertToLowercase(originalId);
            if (noDashesAndLowercase.length() == 32 && isLowerHex(noDashesAndLowercase)) {
                // 32 chars and lowerhex - it's now a valid 128 bit ID.
                return noDashesAndLowercase;
            }
        }

        // It wasn't a UUID, so return null.
        return null;
    }

    protected String stripDashesAndConvertToLowercase(String orig) {
        if (orig == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(orig.length());
        for (int i = 0; i < orig.length(); i++) {
            char nextChar = orig.charAt(i);
            if (nextChar != '-') {
                sb.append(Character.toLowerCase(nextChar));
            }
        }

        return sb.toString();
    }
}
