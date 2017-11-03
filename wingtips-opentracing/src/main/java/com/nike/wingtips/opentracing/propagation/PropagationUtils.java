package com.nike.wingtips.opentracing.propagation;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class PropagationUtils {
    final boolean urlEncoding;

    public PropagationUtils(boolean urlEncoding) {
        this.urlEncoding = urlEncoding;
    }

    public String encodedValue(String value) {
        if (!urlEncoding) {
            return value;
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    public String decodedValue(String value) {
        if (!urlEncoding) {
            return value;
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    public String prefixedKey(String key, String prefix) {
        return prefix + key;
    }

    public String unprefixedKey(String key, String prefix) {
        return key.substring(prefix.length());
    }
}
