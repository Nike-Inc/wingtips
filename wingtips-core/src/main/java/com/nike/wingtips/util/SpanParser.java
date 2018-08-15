package com.nike.wingtips.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;

public class SpanParser {

	private static final Logger logger = LoggerFactory.getLogger(SpanParser.class);
	
	/** The name of the trace ID field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #getTraceId()}. */
    public static final String TRACE_ID_FIELD = "traceId";
    /** The name of the parent span ID field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #getParentSpanId()}. */
    public static final String PARENT_SPAN_ID_FIELD = "parentSpanId";
    /** The name of the span ID field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #getSpanId()}. */
    public static final String SPAN_ID_FIELD = "spanId";
    /** The name of the span name field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #getSpanName()}. */
    public static final String SPAN_NAME_FIELD = "spanName";
    /** The name of the sampleable field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #isSampleable()}. */
    public static final String SAMPLEABLE_FIELD = "sampleable";
    /** The name of the user ID field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #getUserId()}. */
    public static final String USER_ID_FIELD = "userId";
    /** The name of the span purpose field when serializing to JSON (see {@link #toJSON()}. Corresponds to {@link #getSpanPurpose()}. */
    public static final String SPAN_PURPOSE_FIELD = "spanPurpose";
    /** The name of the start-time-in-epoch-micros field when serializing/deserializing to/from JSON (see {@link #toJSON()} and {@link #fromJSON(String)}). Corresponds to {@link #getSpanStartTimeNanos()}. */
    public static final String START_TIME_EPOCH_MICROS_FIELD = "startTimeEpochMicros";
    /** The name of the duration-in-nanoseconds field when serializing to JSON (see {@link #toJSON()}. Corresponds to {@link #getDurationNanos()}. */
    public static final String DURATION_NANOS_FIELD = "durationNanos";
    /** The name of the span tags field when serializing to JSON (see {@link #toJSON()}. Corresponds to {@link #getTags()}. */
    public static final String TAGS_FIELD = "tags";
    
	public static Span parseKeyValueFormat(String input) {
		return null;
	}
	
	public static String convertSpanToKeyValueFormat(Span span) {
		 StringBuilder builder = new StringBuilder();

        builder.append(TRACE_ID_FIELD).append("=").append(span.getTraceId());
        builder.append(",").append(PARENT_SPAN_ID_FIELD).append("=").append(span.getParentSpanId());
        builder.append(",").append(SPAN_ID_FIELD).append("=").append(span.getSpanId());
        builder.append(",").append(SPAN_NAME_FIELD).append("=").append(span.getSpanName());
        builder.append(",").append(SAMPLEABLE_FIELD).append("=").append(span.isSampleable());
        builder.append(",").append(USER_ID_FIELD).append("=").append(span.getUserId());
        builder.append(",").append(SPAN_PURPOSE_FIELD).append("=").append(span.getSpanPurpose().name());
        builder.append(",").append(START_TIME_EPOCH_MICROS_FIELD).append("=").append(span.getSpanStartTimeEpochMicros());
        if (span.isCompleted()) {
            builder.append(",").append(DURATION_NANOS_FIELD).append("=").append(span.getDurationNanos());
        }
        if (span.getTags() != null && !span.getTags().isEmpty()) 
        		builder.append(",").append(TAGS_FIELD).append("=[").append(convertMapToKeyValueString(span.getTags())).append("]");
        
        return builder.toString();
	}
	
	/**
     * Calculates and returns the JSON representation of this span instance. We build this manually ourselves to avoid pulling in an extra dependency
     * (e.g. Jackson) just for building a simple JSON string.
     */
	public static String convertSpanToJSON(Span span) {
		StringBuilder builder = new StringBuilder();

        builder.append("{\"").append(TRACE_ID_FIELD).append("\":\"").append(span.getTraceId());
        builder.append("\",\"").append(PARENT_SPAN_ID_FIELD).append("\":\"").append(span.getParentSpanId());
        builder.append("\",\"").append(SPAN_ID_FIELD).append("\":\"").append(span.getSpanId());
        builder.append("\",\"").append(SPAN_NAME_FIELD).append("\":\"").append(span.getSpanName());
        builder.append("\",\"").append(SAMPLEABLE_FIELD).append("\":\"").append(span.isSampleable());
        builder.append("\",\"").append(USER_ID_FIELD).append("\":\"").append(span.getUserId());
        builder.append("\",\"").append(SPAN_PURPOSE_FIELD).append("\":\"").append(span.getSpanPurpose().name());
        builder.append("\",\"").append(START_TIME_EPOCH_MICROS_FIELD).append("\":\"").append(span.getSpanStartTimeEpochMicros());
        if (span.isCompleted()) {
            builder.append("\",\"").append(DURATION_NANOS_FIELD).append("\":\"").append(span.getDurationNanos());
        }
        builder.append("\"");
        if(!span.getTags().isEmpty()) {
        		// Create nested json for the tags
        		builder.append(",\"").append(TAGS_FIELD).append("\":{");
        		builder.append(convertMapToJsonValues(span.getTags())).append("}");
        } 
        builder.append("}");

        return builder.toString();
	}
    
    /**
     * @return The {@link Span} represented by the given key/value string, or null if a proper span could not be deserialized from the given string.
     *          <b>WARNING:</b> This method assumes the string you're trying to deserialize originally came from
     *          {@link #toKeyValueString()}. This assumption allows it to be as fast as possible, not worry about syntactically-correct-but-annoying-to-deal-with whitespace,
     *          not have to use a third party utility, etc.
     */
    public static Span fromKeyValueString(String keyValueStr) {
        try {
        		Map<String,String> map;

        		if(keyValueStringHasTagsPresent(keyValueStr)) {
        			//Pull tags into their own map
        			Map<String,String> tags = parseTagsFromFullKeyValueString(keyValueStr);
        			//Strip out the tags
        			String keyValueStrWithoutTags = removeTagsFromKeyValueString(keyValueStr);
        			//Parse the remaining keyValue pairs
        			map = getMapFromKeyValueString(keyValueStrWithoutTags);
        			return fromKeyValueMap(map, tags);
            } else {
            		map = getMapFromKeyValueString(keyValueStr);
            		return fromKeyValueMap(map, null);
            }
            
        } catch (Exception e) {
            logger.error("Error extracting Span from key/value string. Defaulting to null. bad_span_key_value_string={}", keyValueStr, e);
            return null;
        }
    }

    private static boolean keyValueStringHasTagsPresent(String keyValueStr) {
    		return keyValueStr.contains(TAGS_FIELD + "=[");
    }
    private static Map<String,String> parseTagsFromFullKeyValueString(String keyValueStr) {
    		String tagsKeyValueString = parseTagKeyValueString(keyValueStr);
    		return getMapFromKeyValueString(tagsKeyValueString);
    }
    
    /** Returns only the key value pairs, not the tags=[   and closing   ]  **/
    protected static String parseTagKeyValueString(String keyValueStr) {
    		// find where it starts and then move forward the length of the pattern
		int tagStart = keyValueStr.indexOf( TAGS_FIELD+"=[" ) + TAGS_FIELD.length() + 2;
		int tagEnd = keyValueStr.indexOf("]");
		String tagsOnly = keyValueStr.substring(tagStart, tagEnd);
		return tagsOnly;
    }
    
    private static String removeTagsFromKeyValueString(String keyValueStrWithTags) {
    		String tagStringToRemove = "," + TAGS_FIELD + "=[" + parseTagKeyValueString(keyValueStrWithTags) + "]";
    		return keyValueStrWithTags.replace(tagStringToRemove, "");
    }
    
    /** 
     * @param keyValueStr Should be in format {@code key=value,key2=value2}
     */
    private static Map<String,String> getMapFromKeyValueString(String keyValueStr) {
    		// Create a map of keys to values.
        Map<String, String> map = new HashMap<>();

        // Split on the commas that separate the key/value pairs.
        String[] fieldPairs = keyValueStr.split(",");
        for (String fieldPair : fieldPairs) {
            // Split again on the equals character that separate the field's key from its value.
            String[] keyVal = fieldPair.split("=");
            map.put(keyVal[0], keyVal[1]);
        }
        return map;
    }
    /**
     * @return The {@link Span} represented by the given JSON string, or null if a proper span could not be deserialized from the given string.
     *          <b>WARNING:</b> This method assumes the JSON you're trying to deserialize originally came from {@link #toJSON()}.
     *          This assumption allows it to be as fast as possible, not have to check for malformed JSON, not worry about syntactically-correct-but-annoying-to-deal-with whitespace,
     *          not have to use a third party utility like Jackson, etc.
     */
    public static Span fromJSON(String json) {
        try {
        		Map<String,String> tags = null;
        		Map<String,String> map;
            if(json.contains("\""+TAGS_FIELD+"\":{")) {
            		tags = parseTagsFromNestedJson(json);
            		String jsonWithoutNestedTags = removeTagsFromJson(json);
            		map = parseSingleKeyValueJson(jsonWithoutNestedTags); 
            } else {
            		map = parseSingleKeyValueJson(json);
            }

            return fromKeyValueMap(map, tags);
        } catch (Exception e) {
            logger.error("Error extracting Span from JSON. Defaulting to null. bad_span_json={}", json, e);
            return null;
        }
    }
    
    protected static Map<String,String> parseTagsFromNestedJson(String jsonWithNestedTagValues) {
    		String tagsOnly = parseNestedTagJson(jsonWithNestedTagValues);
		return parseSingleKeyValueJson(tagsOnly);
    }
    
    /** @return the inner set of tag pairs with surrounding { }  **/
    protected static String parseNestedTagJson(String jsonWithNestedTagValues) {
    		int tagStart = jsonWithNestedTagValues.indexOf(TAGS_FIELD + "\":") + TAGS_FIELD.length() + 2;
		int tagEnd = jsonWithNestedTagValues.indexOf("}") + 1;
		String tagsOnly = jsonWithNestedTagValues.substring(tagStart, tagEnd);
		return tagsOnly;
    }
    
    protected static String removeTagsFromJson(String jsonWithNestedValues) {
    		String fullTagJson = ",\"" + TAGS_FIELD + "\":" + parseNestedTagJson(jsonWithNestedValues);
    		return jsonWithNestedValues.replace(fullTagJson, "");
    }
    
    protected static String convertMapToJsonValues(Map<String,String> map) {
    		StringBuilder builder = new StringBuilder();
    		Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<String,String> tag = (Map.Entry<String,String>)it.next();
			builder.append("\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append("\"");
			 if(it.hasNext())
	        		builder.append(",");
		}
	    return builder.toString();
    }
    
    /**
     * This method expects no nested JSON values. For example {tags: {tag1:val1,tag2:val2}} will throw an exception.
     * @param json
     * @return
     */
    private static Map<String,String> parseSingleKeyValueJson(String json) {
    		// Create a map of JSON field keys to values.
        Map<String, String> map = new HashMap<>();

        // Strip off the {" and "} at the beginning/end.
        String innerJsonCore = json.substring(2, json.length() - 2);
        // Split on the doublequotes-comma-doublequotes that separate the fields.
        String[] fieldPairs = innerJsonCore.split("\",\"");
        for (String fieldPair : fieldPairs) {
            // Split again on the doublequotes-colon-doublequotes that separate the field's key from its value. At this point all double-quotes have been stripped off
            // and we can just map the key to the value.
            String[] keyVal = fieldPair.split("\":\"");
            map.put(keyVal[0], keyVal[1]);
        }
        return map;
    }

    private static Span fromKeyValueMap(Map<String, String> map, Map<String,String> tags) {
        // Use the map to get the field values for the span.
        String traceId = nullSafeGetString(map, TRACE_ID_FIELD);
        String spanId = nullSafeGetString(map, SPAN_ID_FIELD);
        String parentSpanId = nullSafeGetString(map, PARENT_SPAN_ID_FIELD);
        String spanName = nullSafeGetString(map, SPAN_NAME_FIELD);
        Boolean sampleable = nullSafeGetBoolean(map, SAMPLEABLE_FIELD);
        if (sampleable == null)
            throw new IllegalStateException("Unable to parse " + SAMPLEABLE_FIELD + " from JSON");
        String userId = nullSafeGetString(map, USER_ID_FIELD);
        Long startTimeEpochMicros = nullSafeGetLong(map, START_TIME_EPOCH_MICROS_FIELD);
        if (startTimeEpochMicros == null)
            throw new IllegalStateException("Unable to parse " + START_TIME_EPOCH_MICROS_FIELD + " from JSON");
        Long durationNanos = nullSafeGetLong(map, DURATION_NANOS_FIELD);
        SpanPurpose spanPurpose = nullSafeGetSpanPurpose(map, SPAN_PURPOSE_FIELD);
        return new Span(traceId, parentSpanId, spanId, spanName, sampleable, userId, spanPurpose, startTimeEpochMicros, null, durationNanos, tags);
    }
    
    /**
     * @todo Could have a ConcurrentModificationException if tags are added while iterating through
     */
    static String convertMapToKeyValueString(Map<String, String> map) {
    		StringBuilder builder = new StringBuilder();
		if(map != null && map.size() > 0) {
			Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
		    while (it.hasNext()) {
		        Map.Entry<String,String> pair = (Map.Entry<String,String>)it.next();
		        builder.append(pair.getKey()).append("=").append(pair.getValue());
		        if(it.hasNext())
		        		builder.append(",");
		    }
		} 

		return builder.toString();
    }
     
    private static String nullSafeGetString(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.equals("null"))
            return null;

        return value;
    }

    private static Long nullSafeGetLong(Map<String, String> map, String key) {
        String value = nullSafeGetString(map, key);
        if (value == null)
            return null;

        return Long.parseLong(value);
    }

    private static Boolean nullSafeGetBoolean(Map<String, String> map, String key) {
        String value = nullSafeGetString(map, key);
        if (value == null)
            return null;

        return Boolean.parseBoolean(value);
    }

    private static SpanPurpose nullSafeGetSpanPurpose(Map<String, String> map, String key) {
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
}
