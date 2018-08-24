package com.nike.wingtips.util.parser;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("WeakerAccess")
public class SpanParser {

    private static final Logger logger = LoggerFactory.getLogger(SpanParser.class);

    // Intentionally protected - use the static methods.
    protected SpanParser() { /* do nothing */ }

    /**
     * The name of the trace ID field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#getTraceId()}.
     */
    public static final String TRACE_ID_FIELD = "traceId";
    /**
     * The name of the parent span ID field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#getParentSpanId()}.
     */
    public static final String PARENT_SPAN_ID_FIELD = "parentSpanId";
    /**
     * The name of the span ID field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#getSpanId()}.
     */
    public static final String SPAN_ID_FIELD = "spanId";
    /**
     * The name of the span name field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#getSpanName()}.
     */
    public static final String SPAN_NAME_FIELD = "spanName";
    /**
     * The name of the sampleable field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#isSampleable()}.
     */
    public static final String SAMPLEABLE_FIELD = "sampleable";
    /**
     * The name of the user ID field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#getUserId()}.
     */
    public static final String USER_ID_FIELD = "userId";
    /**
     * The name of the span purpose field when serializing to JSON (see {@link
     * #convertSpanToJSON(Span)}. Corresponds to {@link Span#getSpanPurpose()}.
     */
    public static final String SPAN_PURPOSE_FIELD = "spanPurpose";
    /**
     * The name of the start-time-in-epoch-micros field when serializing/deserializing to/from JSON (see {@link
     * #convertSpanToJSON(Span)} and {@link #fromJSON(String)}). Corresponds to {@link Span#getSpanStartTimeNanos()}.
     */
    public static final String START_TIME_EPOCH_MICROS_FIELD = "startTimeEpochMicros";
    /**
     * The name of the duration-in-nanoseconds field when serializing to JSON (see {@link
     * #convertSpanToJSON(Span)}. Corresponds to {@link Span#getDurationNanos()}.
     */
    public static final String DURATION_NANOS_FIELD = "durationNanos";
    /**
     * The name of the span tags field when serializing to JSON (see {@link
     * #convertSpanToJSON(Span)}. Corresponds to {@link Span#getTags()}.
     */
    public static final String TAGS_FIELD = "tags";

    /**
     * The prefix that will be added to every {@link Span#getTags()} tag key when serializing a {@link Span} to
     * key/value format. See {@link #convertSpanToKeyValueFormat(Span)}.
     */
    public static final String KEY_VALUE_TAG_PREFIX = "tag_";

    /**
     * Calculates and returns the JSON representation of this span instance. We build this manually ourselves to
     * avoid pulling in an extra dependency (e.g. Jackson) just for building a simple JSON string.
     *
     * <p>NOTE: You should call {@link Span#toJSON()} directly instead of this method, as that {@link Span#toJSON()}
     * instance method caches the result. This can have significant performance impact in some scenarios.
     */
    public static String convertSpanToJSON(Span span) {
        StringBuilder builder = new StringBuilder();

        builder.append("{\"").append(TRACE_ID_FIELD).append("\":\"").append(escapeJson(span.getTraceId())).append('\"');
        builder.append(",\"").append(PARENT_SPAN_ID_FIELD).append("\":\"").append(escapeJson(span.getParentSpanId())).append('\"');
        builder.append(",\"").append(SPAN_ID_FIELD).append("\":\"").append(escapeJson(span.getSpanId())).append('\"');
        builder.append(",\"").append(SPAN_NAME_FIELD).append("\":\"").append(escapeJson(span.getSpanName())).append('\"');
        builder.append(",\"").append(SAMPLEABLE_FIELD).append("\":\"").append(span.isSampleable()).append('\"');
        builder.append(",\"").append(USER_ID_FIELD).append("\":\"").append(escapeJson(span.getUserId())).append('\"');
        builder.append(",\"").append(SPAN_PURPOSE_FIELD).append("\":\"").append(span.getSpanPurpose().name()).append('\"');
        builder.append(",\"").append(START_TIME_EPOCH_MICROS_FIELD).append("\":\"").append(span.getSpanStartTimeEpochMicros()).append('\"');

        if (span.isCompleted()) {
            builder.append(",\"").append(DURATION_NANOS_FIELD).append("\":\"").append(span.getDurationNanos()).append('\"');
        }

        if(!span.getTags().isEmpty()) {
            // Create nested json for the tags
            builder.append(",\"").append(TAGS_FIELD).append("\":{");

            boolean first = true;
            for (Map.Entry<String, String> tagEntry : span.getTags().entrySet()) {
                if (!first) {
                    builder.append(',');
                }

                String escapedKey = escapeJson(tagEntry.getKey());
                String escapedValue = escapeJson(tagEntry.getValue());

                builder.append('\"').append(escapedKey).append("\":\"").append(escapedValue).append('\"');

                first = false;
            }

            builder.append("}");
        }

        builder.append("}");

        return builder.toString();
    }

    /**
     * @return The {@link Span} represented by the given JSON string, or null if a proper span could not be
     * deserialized from the given string.
     *
     * <p><b>WARNING:</b> This method assumes the JSON you're trying to deserialize originally came from {@link
     * #convertSpanToJSON(Span)}. This assumption allows it to take some shortcuts while deserializing, and allows us
     * to accomplish deserialization efficiently without needing to pull in a third-party dependency like Jackson.
     * If you try to use this method on a JSON string that didn't come from {@link #convertSpanToJSON(Span)},
     * then it will likely fail.
     */
    public static Span fromJSON(String json) {
        try {
            JsonDeserializationResult mainResult = deserializeJsonObject(json, 0);
            Map<String, String> mainMap = mainResult.levelOneKeyValuePairs;

            JsonDeserializationResult tagsResult = mainResult.subObjects.get(TAGS_FIELD);
            Map<String, String> tagsMap = (tagsResult == null) ? null : tagsResult.levelOneKeyValuePairs;

            return fromKeyValueMap(mainMap, tagsMap);
        } catch (Exception e) {
            logger.error("Error extracting Span from JSON. Defaulting to null. bad_span_json={}", json, e);
            return null;
        }
    }

    protected static class JsonDeserializationResult {
        final Map<String, String> levelOneKeyValuePairs = new HashMap<>();
        final Map<String, JsonDeserializationResult> subObjects = new HashMap<>();
        final AtomicInteger jsonEndObjectIndex = new AtomicInteger(Integer.MAX_VALUE);
    }

    protected enum ParsingState {
        EXPECT_KEY_START_QUOTES,
        EXTRACTING_KEY,
        EXPECT_KEY_VALUE_DELIMITER_CHAR,
        EXPECT_VALUE_START_CHAR, // For JSON this might be quotes '"', or object-start '{', or array/list start '['
        EXTRACTING_VALUE,
        EXPECT_COMMA_BEFORE_NEW_KEY
    }
    
    protected static JsonDeserializationResult deserializeJsonObject(String jsonStr, int deserializationStartIndex) {
        JsonDeserializationResult result = new JsonDeserializationResult();

        // Make sure our JSON string starts with a '{' char.
        if (jsonStr.charAt(deserializationStartIndex) != '{') {
            throw new IllegalStateException(
                "Expected JSON string at index " + deserializationStartIndex + " to be a '{' char."
            );
        }

        int i = deserializationStartIndex + 1; // Wind past the '{' char.
        int strLen = jsonStr.length();
        int extractKeyStartIndex = i;
        int extractKeyEndIndex = i;
        int extractValueStartIndex = i;
        int numPrecedingBackslashes = 0;
        ParsingState state = ParsingState.EXPECT_KEY_START_QUOTES;
        while (i < strLen) {
            char c = jsonStr.charAt(i);
            if (state == ParsingState.EXPECT_KEY_START_QUOTES) {
                if (c == '\"') {
                    // Found the quotes. The next character will be the first character of the key.
                    extractKeyStartIndex = i + 1;

                    // Switch state to extracting key.
                    state = ParsingState.EXTRACTING_KEY;
                }
                else {
                    throw new IllegalStateException(
                        "Span parsing error: Expected but did not find quotes '\"' character for key-start at "
                        + "index " + i
                    );
                }
            }
            else if (state == ParsingState.EXTRACTING_KEY) {
                if (isUnescapedQuotes(c, numPrecedingBackslashes)) {
                    // We found the end of the key (a non-escaped quotes). Mark it.
                    extractKeyEndIndex = i;

                    // Switch state to looking for the key/value delimiter.
                    state = ParsingState.EXPECT_KEY_VALUE_DELIMITER_CHAR;
                }
            }
            else if (state == ParsingState.EXPECT_KEY_VALUE_DELIMITER_CHAR) {
                // We expect this to be a colon character.
                if (c == ':') {
                    // Found the colon. The next character should be the value-start character
                    //      (object-start '{' or value-start '"').
                    state = ParsingState.EXPECT_VALUE_START_CHAR;
                }
                else {
                    throw new IllegalStateException(
                        "Span parsing error: Expected but did not find colon ':' character for key/value delimiter "
                        + "at index " + i
                    );
                }
            }
            else if (state == ParsingState.EXPECT_VALUE_START_CHAR) {
                // If the next char is an object-start '{', then we recursively call this method to extract the JSON
                //      object. Otherwise we expect a quotes char for value-start.
                if (c == '{') {
                    // JSON-object-start. Deserialize this subobject.
                    JsonDeserializationResult subObject = deserializeJsonObject(jsonStr, i);

                    // Extract the key associated with the subObject.
                    String key = jsonStr.substring(extractKeyStartIndex, extractKeyEndIndex);
                    key = unescapeJson(key);

                    // Add this key->subobject info to our result.
                    result.subObjects.put(key, subObject);

                    // Wind the index forward to wherever the subObject stopped.
                    i = subObject.jsonEndObjectIndex.get();

                    // Reset the numPrecedingBackslashes counter.
                    numPrecedingBackslashes = 0;

                    // Switch state to looking for the comma before the next key.
                    state = ParsingState.EXPECT_COMMA_BEFORE_NEW_KEY;
                }
                else if (c == '\"') {
                    // Value-start quotes. The next time the loop iterates it will be pointing at the value. Mark
                    //      the index for value extraction and switch state.
                    extractValueStartIndex = i + 1;
                    state = ParsingState.EXTRACTING_VALUE;
                }
                // TODO: We'll need to handle JSON arrays [] here once Span has support for timestamped annotations.
                else {
                    throw new IllegalStateException(
                        "Span parsing error: Expected but did not find left curly brace '{' or quotes '\"' character "
                        + "for value-start at index " + i
                    );
                }
            }
            else if (state == ParsingState.EXTRACTING_VALUE) {
                if (isUnescapedQuotes(c, numPrecedingBackslashes)) {
                    // We found the end of the value (a non-escaped quotes). Extract key and value, and put them in
                    //      the level one key/value map.
                    String key = jsonStr.substring(extractKeyStartIndex, extractKeyEndIndex);
                    String value = jsonStr.substring(extractValueStartIndex, i);
                    key = unescapeJson(key);
                    value = unescapeJson(value);

                    result.levelOneKeyValuePairs.put(key, value);

                    // We expect a comma next.
                    state = ParsingState.EXPECT_COMMA_BEFORE_NEW_KEY;
                }
            }
            else if (state == ParsingState.EXPECT_COMMA_BEFORE_NEW_KEY) {
                // If the character is a close-object '}', then we're done and should return the result
                //      we've gathered so far.
                if (c == '}') {
                    // Close-object '}' curly-brace was found. Return the result after marking the
                    //      close-object index.
                    result.jsonEndObjectIndex.set(i);
                    return result;
                }
                else if (c == ',') {
                    // Comma was found. Next we expect the key-start quotes.
                    state = ParsingState.EXPECT_KEY_START_QUOTES;
                }
                else {
                    throw new IllegalStateException(
                        "Span parsing error: Expected but did not find right curly brace '}' or comma ',' character "
                        + "after value-end at index " + i
                    );
                }
            }
            else {
                // Should never happen.
                throw new IllegalStateException("Unhandled state: " + state);
            }

            if (c == '\\') {
                numPrecedingBackslashes++;
            }
            else {
                numPrecedingBackslashes = 0;
            }

            i++;
        }

        // JSON should always explicitly short circuit in EXPECT_COMMA_BEFORE_NEW_KEY state due to finding
        //      close-object '}' character. If we reach here then parsing failed.
        throw new IllegalStateException(
            "Span parsing error: JSON string did not end with a right curly brace '}'. Instead ended in state: "
            + state.name()
        );
    }

    private static boolean isUnescapedQuotes(
        char charInQuestion, int numPrecedingBackslashes
    ) {
        if (charInQuestion != '\"') {
            // Not quotes, so can't be unescaped quotes.
            return false;
        }

        // The original character in question is a quotes. We know whether it's escaped or not by how many preceding
        //      backslashes it has.
        return (numPrecedingBackslashes % 2) == 0;
    }

    /**
     * Calculates and returns the key/value representation of this span instance. Keys are not surrounded by quotes,
     * but values are. Both keys and values are escaped via {@link #escapeJson(String)}, with keys further being
     * escaped to replace equals '=' and spaces ' ' with their escaped-unicode equivalents. Tag keys will be prefixed
     * with {@link #KEY_VALUE_TAG_PREFIX}.
     *
     * <p>NOTE: You should call {@link Span#toKeyValueString()} directly instead of this method, as that {@link
     * Span#toKeyValueString()} instance method caches the result. This can have significant performance impact in some
     * scenarios.
     */
    public static String convertSpanToKeyValueFormat(Span span) {
        StringBuilder builder = new StringBuilder();

        builder.append(TRACE_ID_FIELD).append("=\"").append(escapeJson(span.getTraceId())).append('\"');
        builder.append(",").append(PARENT_SPAN_ID_FIELD).append("=\"").append(escapeJson(span.getParentSpanId())).append('\"');
        builder.append(",").append(SPAN_ID_FIELD).append("=\"").append(escapeJson(span.getSpanId())).append('\"');
        builder.append(",").append(SPAN_NAME_FIELD).append("=\"").append(escapeJson(span.getSpanName())).append('\"');
        builder.append(",").append(SAMPLEABLE_FIELD).append("=\"").append(span.isSampleable()).append('\"');
        builder.append(",").append(USER_ID_FIELD).append("=\"").append(escapeJson(span.getUserId())).append('\"');
        builder.append(",").append(SPAN_PURPOSE_FIELD).append("=\"").append(span.getSpanPurpose().name()).append('\"');
        builder.append(",").append(START_TIME_EPOCH_MICROS_FIELD).append("=\"").append(span.getSpanStartTimeEpochMicros()).append('\"');

        // Only output duration if the span is completed.
        if (span.isCompleted()) {
            builder.append(",").append(DURATION_NANOS_FIELD).append("=\"").append(span.getDurationNanos()).append('\"');
        }

        // Output tags if we have any.
        for (Map.Entry<String, String> tagEntry : span.getTags().entrySet()) {
            String sanitizedTagKey = escapeTagKeyForKeyValueFormatSerialization(tagEntry.getKey());

            String escapedTagValue = escapeJson(tagEntry.getValue());
            builder.append(",").append(KEY_VALUE_TAG_PREFIX).append(sanitizedTagKey)
                   .append("=\"").append(escapedTagValue).append('\"');
        }

        return builder.toString();
    }

    protected static String escapeTagKeyForKeyValueFormatSerialization(String key) {
        String escapedKey = escapeJson(key);
        // We also need to escape equals sign if it exists. Don't do this unless we detect an equals sign is contained
        //      in the key, however. Most of the time it won't exist in the key, and we'll save a bunch of time
        //      by not calling String.replace(...).
        if (containsChar(escapedKey, '=')) {
            escapedKey = escapedKey.replace("=", ESCAPED_EQUALS_SIGN);
        }
        // Same with space character.
        if (containsChar(escapedKey, ' ')) {
            escapedKey = escapedKey.replace(" ", ESCAPED_SPACE_CHAR);
        }
        return escapedKey;
    }

    protected static String unescapeTagKeyForKeyValueFormatDeserialization(String escapedKey) {
        // Do the reverse of the escape-tag logic.
        if (escapedKey.contains(ESCAPED_SPACE_CHAR)) {
            escapedKey = escapedKey.replace(ESCAPED_SPACE_CHAR, " ");
        }

        if (escapedKey.contains(ESCAPED_EQUALS_SIGN)) {
            escapedKey = escapedKey.replace(ESCAPED_EQUALS_SIGN, "=");
        }

        return unescapeJson(escapedKey);
    }

    /**
     * @return The {@link Span} represented by the given key/value string, or null if a proper span could not be
     * deserialized from the given string.
     *
     * <p><b>WARNING:</b> This method assumes the string you're trying to deserialize originally came from
     * {@link #convertSpanToKeyValueFormat(Span)}. This assumption allows it to be as fast as possible, not worry
     * about syntactically-correct-but-annoying-to-deal-with whitespace, not have to use a third party utility, etc.
     * If you try to use this method on a string that didn't come from {@link #convertSpanToKeyValueFormat(Span)},
     * then it will likely fail.
     */
    public static Span fromKeyValueString(String keyValueStr) {
        try {
            Map<String, String> spanFieldsMap = new HashMap<>();
            Map<String, String> tagsMap = new HashMap<>();

            int i = 0;
            int strLen = keyValueStr.length();
            int extractKeyStartIndex = 0;
            int extractKeyEndIndex = 0;
            int extractValueStartIndex = 0;
            int numPrecedingBackslashes = 0;
            // The very first character should be the start of the first key, so we'll jump straight into
            //      extracting the key.
            ParsingState state = ParsingState.EXTRACTING_KEY;
            while (i < strLen) {
                char c = keyValueStr.charAt(i);
                if (state == ParsingState.EXTRACTING_KEY) {
                    // Keep going until we find an equals character.
                    if (c == '=') {
                        // We found the end of the key. Mark it.
                        extractKeyEndIndex = i;

                        // Switch state to looking for the value.
                        state = ParsingState.EXPECT_VALUE_START_CHAR;
                    }
                }
                else if (state == ParsingState.EXPECT_VALUE_START_CHAR) {
                    // We expect this to be a quotes character.
                    if (c == '\"') {
                        // Found the quotes. The next character will be the first character of the value.
                        extractValueStartIndex = i + 1;

                        // Switch state to extracting value.
                        state = ParsingState.EXTRACTING_VALUE;
                    }
                    else {
                        throw new IllegalStateException(
                            "Span parsing error: Expected but did not find quotes '\"' character for value-start at "
                            + "index " + i
                        );
                    }
                }
                else if (state == ParsingState.EXTRACTING_VALUE) {
                    if (isUnescapedQuotes(c, numPrecedingBackslashes)) {
                        // We found the end of the value (a non-escaped quotes). Extract key and value, and put them in
                        //      the appropriate map.
                        String key = keyValueStr.substring(extractKeyStartIndex, extractKeyEndIndex);
                        String value = keyValueStr.substring(extractValueStartIndex, i);
                        value = unescapeJson(value);

                        boolean isTag = key.startsWith(KEY_VALUE_TAG_PREFIX);
                        if (isTag) {
                            String keyNoTagPrefix = key.substring(KEY_VALUE_TAG_PREFIX.length());
                            key = unescapeTagKeyForKeyValueFormatDeserialization(keyNoTagPrefix);
                        }

                        if (isTag) {
                            tagsMap.put(key, value);
                        }
                        else {
                            spanFieldsMap.put(key, value);
                        }

                        // Switch state to looking for the next key.
                        state = ParsingState.EXPECT_COMMA_BEFORE_NEW_KEY;
                    }
                }
                else if (state == ParsingState.EXPECT_COMMA_BEFORE_NEW_KEY) {
                    // We expect this to be a comma character.
                    if (c == ',') {
                        // Found the comma. The next character should be the first character of the key.
                        extractKeyStartIndex = i + 1;

                        // Switch state to extracting the key.
                        state = ParsingState.EXTRACTING_KEY;
                    }
                    else {
                        throw new IllegalStateException(
                            "Span parsing error: Expected but did not find comma ',' character after value-end at "
                            + "index " + i
                        );
                    }
                }
                else {
                    // Should never happen.
                    throw new IllegalStateException("Unhandled state: " + state);
                }

                if (c == '\\') {
                    numPrecedingBackslashes++;
                }
                else {
                    numPrecedingBackslashes = 0;
                }
                
                i++;
            }

            if (state != ParsingState.EXPECT_COMMA_BEFORE_NEW_KEY) {
                throw new IllegalStateException(
                    "Span parsing error: key/value string did not end after finding a value. Instead ended in state: "
                    + state.name()
                );
            }

            return fromKeyValueMap(spanFieldsMap, tagsMap);
        } catch (Exception e) {
            logger.error("Error extracting Span from key/value string. Defaulting to null. bad_span_key_value_string={}", keyValueStr, e);
            return null;
        }
    }

    protected static Span fromKeyValueMap(Map<String, String> map, Map<String, String> tags) {
        // Use the map to get the field values for the span.
        String traceId = nullSafeGetString(map, TRACE_ID_FIELD);
        String spanId = nullSafeGetString(map, SPAN_ID_FIELD);
        String parentSpanId = nullSafeGetString(map, PARENT_SPAN_ID_FIELD);
        String spanName = nullSafeGetString(map, SPAN_NAME_FIELD);
        Boolean sampleable = nullSafeGetBoolean(map, SAMPLEABLE_FIELD);
        if (sampleable == null)
            throw new IllegalStateException("Unable to parse " + SAMPLEABLE_FIELD + " from serialized Span");
        String userId = nullSafeGetString(map, USER_ID_FIELD);
        Long startTimeEpochMicros = nullSafeGetLong(map, START_TIME_EPOCH_MICROS_FIELD);
        if (startTimeEpochMicros == null)
            throw new IllegalStateException("Unable to parse " + START_TIME_EPOCH_MICROS_FIELD + " from serialized Span");
        Long durationNanos = nullSafeGetLong(map, DURATION_NANOS_FIELD);
        SpanPurpose spanPurpose = nullSafeGetSpanPurpose(map, SPAN_PURPOSE_FIELD);
        return new Span(
            traceId, parentSpanId, spanId, spanName, sampleable, userId, spanPurpose, startTimeEpochMicros,
            null, durationNanos, tags
        );
    }

    protected static String nullSafeGetString(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.equals("null"))
            return null;

        return value;
    }

    protected static Long nullSafeGetLong(Map<String, String> map, String key) {
        String value = nullSafeGetString(map, key);
        if (value == null)
            return null;

        return Long.parseLong(value);
    }

    @SuppressWarnings("SameParameterValue")
    protected static Boolean nullSafeGetBoolean(Map<String, String> map, String key) {
        String value = nullSafeGetString(map, key);
        if (value == null)
            return null;

        return Boolean.parseBoolean(value);
    }

    @SuppressWarnings("SameParameterValue")
    protected static SpanPurpose nullSafeGetSpanPurpose(Map<String, String> map, String key) {
        String value = nullSafeGetString(map, key);
        if (value == null)
            return null;

        try {
            return SpanPurpose.valueOf(value);
        }
        catch(Exception ex) {
            logger.warn("Unable to parse \"{}\" to a SpanPurpose enum. Received exception: {}", value, ex.toString());
            return null;
        }
    }

    /**
     * Escapes the given String using minimal JSON rules. See
     * <a href="https://tools.ietf.org/html/rfc7159#section-7">RFC 7159 Section 7</a> for full details, but in
     * particular note that we are only escaping the bare minimum required characters: quotation mark, reverse solidus
     * (backslash), and the control characters (U+0000 through U+001F).
     *
     * @param orig The original string that needs JSON escaping.
     * @return The given original string, with quotation marks, backslashes, and control characters escaped so that it
     * is ready for use as a JSON string.
     */
    public static String escapeJson(String orig) {
        if (orig == null) {
            return null;
        }

        StringBuilder sb = null;
        for (int i = 0; i < orig.length(); i++) {
            char nextChar = orig.charAt(i);
            if (needsJsonEscaping(nextChar)) {
                // Initialize the StringBuilder if necessary
                if (sb == null) {
                    // Create it to be the original string's size, plus a few extra to accommodate escape characters.
                    sb = new StringBuilder(orig.length() + 16);
                    // Add everything from the beginning of the string up to, but not including, the current character.
                    sb.append(orig, 0, i);
                }

                // Get the escaped string value associated with the given char, and append it.
                String escapedValue = getJsonEscapedValueForChar(nextChar);
                if (escapedValue == null) {
                    logger.warn(
                        "Somehow found a character that needs escaping, but getJsonEscapedValueForChar(char) "
                        + "came back null. This shouldn't happen! char_int_value={}", (int)nextChar
                    );
                    // Shouldn't ever reach here, but if we do, just append the character as-is.
                    sb.append(nextChar);
                }
                else {
                    sb.append(escapedValue);
                }
            }
            else if (sb != null) {
                // Not a char that needs escaping, but we have been forced to build an escaped string, so append
                //      this char as-is.
                sb.append(nextChar);
            }
        }

        // If sb is null then no chars needed escaping, and we can just return the original string as-is.
        if (sb == null) {
            return orig;
        }

        return sb.toString();
    }

    /**
     * Unescapes the given String using minimal JSON rules. See
     * <a href="https://tools.ietf.org/html/rfc7159#section-7">RFC 7159 Section 7</a> for full details, but in
     * particular note that we are only unescaping the bare minimum required characters: quotation mark, reverse solidus
     * (backslash), and the control characters (U+0000 through U+001F).
     *
     * <p>This effectively reverses the escaping done by {@link #escapeJson(String)}.
     *
     * <p>NOTE: This uses {@link String#replace(CharSequence, CharSequence)} to do its work, therefore it is pretty
     * slow, and should not be used for general-purpose JSON unescaping. We do an optimistic check for backslash first,
     * though, and if no backslash is found then we immediately return without doing any of the unescape logic. So for
     * the common/normal case related to {@link Span} stuff this is extremely fast (it's unusual to need unescaping for
     * anything in a normal {@link Span}). But if your {@link Span} does have data that needs unescaping, this method
     * isn't particularly great.
     *
     * <p>TODO: Maybe someday find a more efficient way to unescape? Might never be necessary - it's unlikely this
     * would be used at runtime in a way that requires blistering speed.
     *
     * @param escaped The escaped string that needs JSON unescaping.
     * @return The given original string, with quotation marks, backslashes, and control characters escaped so that it
     * is ready for use as a JSON string.
     */
    public static String unescapeJson(String escaped) {
        if (escaped == null) {
            return null;
        }

        // Most strings we run across won't actually need unescaping, so look for that optimistic case first as
        //      it would save a bunch of time.
        if (!containsChar(escaped, '\\')) {
            return escaped;
        }

        // There's a backslash, so we need to unescape. We'll do it the slow-but-safe way of calling String.replace(...)
        //      for every escaped mapping to turn it back into the original char.
        String unescaped = escaped;
        for (int i = 0; i < JSON_ESCAPE_CHAR_MAPPINGS.length; i++) {
            String escapedString = JSON_ESCAPE_CHAR_MAPPINGS[i];

            if (escapedString != null) {
                String unescapedCharAsString = String.valueOf((char) i);

                unescaped = unescaped.replace(escapedString, unescapedCharAsString);
            }
        }

        return unescaped;
    }

    protected static boolean containsChar(String str, char c) {
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                return true;
            }
        }

        return false;
    }

    /**
     * This JSON_ESCAPE_CHAR_MAPPINGS array was inspired by the way Jackson does escaping - see Jackson's
     * {@code CharType} class. You can index into the array using the character in question, and the result you'll
     * get back is the escaped-string that should be used in place of that character when escaping for JSON, or null
     * if the character should not be escaped.
     */
    protected static final String[] JSON_ESCAPE_CHAR_MAPPINGS;
    protected static final String ESCAPED_EQUALS_SIGN = "\\u003D";
    protected static final String ESCAPED_SPACE_CHAR = "\\u0020";
    static {
        String[] jsonEscapeCharMappings = new String[128];

        // Add the control chars mappings using full unicode representation.
        char firstControlChar = '\u0000';
        char lastControlChar = '\u001F';
        for (int i = firstControlChar; i <= lastControlChar; i++) {
            // Generate the hex representation of the int value for the char (uppercased).
            String hex = Integer.toHexString(i).toUpperCase();

            // Build the unicode string to represent the char. Start with the backslash-u.
            StringBuilder sb = new StringBuilder(6);
            sb.append("\\u");

            // Pad with prepending zeros if necessary so that the hex representation is 4 characters long.
            int numPaddingNeeded = Math.max(0, (4 - hex.length()));
            for (int pad = 0; pad < numPaddingNeeded; pad++) {
                sb.append("0");
            }

            // Add the hex value.
            sb.append(hex);

            // Set the char->escaped mapping.
            jsonEscapeCharMappings[i] = sb.toString();
        }

        // Some of the JSON escapes have short versions that can be used in place of the full unicode representation.
        //      Set the mappings for those shortened escapes. Note that forward slash is an optional escape, and we
        //      don't do it. We only escape strictly-required characters.
        jsonEscapeCharMappings['"'] = "\\\"";
        jsonEscapeCharMappings['\\'] = "\\\\";
        jsonEscapeCharMappings['\b'] = "\\b";
        jsonEscapeCharMappings['\t'] = "\\t";
        jsonEscapeCharMappings['\f'] = "\\f";
        jsonEscapeCharMappings['\n'] = "\\n";
        jsonEscapeCharMappings['\r'] = "\\r";
        JSON_ESCAPE_CHAR_MAPPINGS = jsonEscapeCharMappings;
    }

    protected static String getJsonEscapedValueForChar(char c) {
        if ((int)c >= JSON_ESCAPE_CHAR_MAPPINGS.length) {
            // No need to escape.
            return null;
        }

        return JSON_ESCAPE_CHAR_MAPPINGS[c];
    }

    protected static boolean needsJsonEscaping(char nextChar) {
        return    (nextChar == '"')
               || (nextChar == '\\')
               || (nextChar < 32);
    }
}
