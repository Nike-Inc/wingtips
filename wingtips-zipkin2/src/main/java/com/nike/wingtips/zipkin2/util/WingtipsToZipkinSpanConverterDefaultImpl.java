package com.nike.wingtips.zipkin2.util;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Span.TimestampedAnnotation;
import com.nike.wingtips.TraceAndSpanIdGenerator;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import zipkin2.Endpoint;

/**
 * Default implementation of {@link WingtipsToZipkinSpanConverter} that knows how to convert a Wingtips span to a
 * Zipkin span.
 *
 * <p>NOTE: Although Wingtips encourages conforming to the
 * <a href="https://github.com/openzipkin/b3-propagation">Zipkin B3</a> specification for IDs (16 character/64 bit
 * lowercase hexadecimal values, with an option for 32 char/128 bit lowerhex for trace IDs), Wingtips itself treats
 * IDs as strings - you can pass any string for IDs and Wingtips will happily handle it. There's no enforcement of
 * any specific format in Wingtips. This works ok as long as you don't need to integrate with another system that
 * has more strict requirements on ID formats, i.e. when you want to send Wingtips span data to Zipkin. But if you do
 * need to integrate with Zipkin for visualization of your traces, *and* you can't force your callers to use the proper
 * ID format, then you'd be in trouble. This class handles this situation by giving you an option to "sanitize" IDs
 * if necessary. You can enable sanitization by using the alternate {@link
 * WingtipsToZipkinSpanConverterDefaultImpl#WingtipsToZipkinSpanConverterDefaultImpl(boolean)} constructor and passing
 * true. If you enable sanitization and this class sees a badly formatted ID, then it will convert it to the proper
 * lowerhex format, add a {@link #SANITIZED_ID_LOG_MSG log message} with the original and sanitized IDs for correlation,
 * and add a Zipkin {@code invalid.[trace/span/parent]_id} tag with a value of the original ID. The sanitization is
 * done in a deterministic way so that the same original ID input will always be sanitized into the same output.
 *
 * @author Nic Munroe
 */
@SuppressWarnings("WeakerAccess")
public class WingtipsToZipkinSpanConverterDefaultImpl implements WingtipsToZipkinSpanConverter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String SANITIZED_ID_LOG_MSG = "Detected invalid ID format. orig_id={}, sanitized_id={}";

    protected final boolean enableIdSanitization;

    public WingtipsToZipkinSpanConverterDefaultImpl() {
        // Disable ID sanitization by default - this should be something that users consciously opt-in for.
        this(false);
    }

    public WingtipsToZipkinSpanConverterDefaultImpl(boolean enableIdSanitization) {
        this.enableIdSanitization = enableIdSanitization;
    }

    @Override
    public zipkin2.Span convertWingtipsSpanToZipkinSpan(Span wingtipsSpan, Endpoint zipkinEndpoint) {
        long durationMicros = TimeUnit.NANOSECONDS.toMicros(wingtipsSpan.getDurationNanos());

        String spanId = sanitizeIdIfNecessary(wingtipsSpan.getSpanId(), false);
        String traceId = sanitizeIdIfNecessary(wingtipsSpan.getTraceId(), true);
        String parentId = sanitizeIdIfNecessary(wingtipsSpan.getParentSpanId(), false);

        final zipkin2.Span.Builder spanBuilder = zipkin2.Span
            .newBuilder()
            .id(spanId)
            .name(wingtipsSpan.getSpanName())
            .parentId(parentId)
            .traceId(traceId)
            .timestamp(wingtipsSpan.getSpanStartTimeEpochMicros())
            .duration(durationMicros)
            .localEndpoint(zipkinEndpoint)
            .kind(determineZipkinKind(wingtipsSpan));
        
        // Iterate over existing wingtips tags and add them to the zipkin builder.
        for (Map.Entry<String, String> tagEntry : wingtipsSpan.getTags().entrySet()) {
            spanBuilder.putTag(tagEntry.getKey(), tagEntry.getValue());
        }
            
        if (!spanId.equals(wingtipsSpan.getSpanId())) {
            spanBuilder.putTag("invalid.span_id", wingtipsSpan.getSpanId());
        }
        if (!traceId.equals(wingtipsSpan.getTraceId())) {
            spanBuilder.putTag("invalid.trace_id", wingtipsSpan.getTraceId());
        }
        if (parentId != null && !parentId.equals(wingtipsSpan.getParentSpanId())) {
            spanBuilder.putTag("invalid.parent_id", wingtipsSpan.getParentSpanId());
        }

        // Iterate over existing wingtips annotations and add them to the zipkin builder.
        for (TimestampedAnnotation wingtipsAnnotation : wingtipsSpan.getTimestampedAnnotations()) {
            spanBuilder.addAnnotation(wingtipsAnnotation.getTimestampEpochMicros(), wingtipsAnnotation.getValue());
        }
        
        return spanBuilder.build();
    }

    @SuppressWarnings("WeakerAccess")
    protected zipkin2.Span.Kind determineZipkinKind(Span wingtipsSpan) {
        SpanPurpose wtsp = wingtipsSpan.getSpanPurpose();

        // Clunky if checks necessary to avoid code coverage gaps with a switch statement
        //      due to unreachable default case. :(
        if (SpanPurpose.SERVER == wtsp) {
            return zipkin2.Span.Kind.SERVER;
        }
        else if (SpanPurpose.CLIENT == wtsp) {
            return zipkin2.Span.Kind.CLIENT;
        }
        else if (SpanPurpose.LOCAL_ONLY == wtsp || SpanPurpose.UNKNOWN == wtsp) {
            // No Zipkin Kind associated with these SpanPurposes.
            return null;
        }
        else {
            // This case should technically be impossible, but in case it happens we'll log a warning and default to
            //      no Zipkin kind.
            logger.warn("Unhandled SpanPurpose type: {}", String.valueOf(wtsp));
            return null;
        }
    }

    protected String sanitizeIdIfNecessary(final String originalId, final boolean allow128Bit) {
        if (!enableIdSanitization) {
            return originalId;
        }

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
                logger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
                return sanitizedId;
            }
        }

        // If the originalId can be parsed as a long, then its sanitized ID is the lowerhex representation of that long.
        Long originalIdAsRawLong = attemptToConvertToLong(originalId);
        if (originalIdAsRawLong != null) {
            String sanitizedId = TraceAndSpanIdGenerator.longToUnsignedLowerHexString(originalIdAsRawLong);
            logger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
            return sanitizedId;
        }

        // If the originalId can be parsed as a UUID and is allowed to be 128 bit,
        //      then its sanitized ID is that UUID with the dashes ripped out and forced lowercase.
        if (allow128Bit) {
            String sanitizedId = attemptToSanitizeAsUuid(originalId);
            if (sanitizedId != null) {
                logger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
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
        String sanitizedId = DigestUtils.sha256Hex(originalId).toLowerCase().substring(0, allowedNumChars);
        logger.info(SANITIZED_ID_LOG_MSG, originalId, sanitizedId);
        return sanitizedId;
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
            logger.warn(
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
