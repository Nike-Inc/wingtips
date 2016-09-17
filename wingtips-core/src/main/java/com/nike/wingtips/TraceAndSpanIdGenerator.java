package com.nike.wingtips;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

/**
 * ID generation class for use with trace IDs, span IDs, and parent span IDs for the {@link Span} class. Call the static {@link #generateId()} method whenever you
 * need to create a new ID. These IDs are fundamentally 64-bit longs, but they are returned by {@link #generateId()} encoded as unsigned and in hexadecimal format.
 * To turn those strings back into Java long primitives you can call {@link #unsignedLowerHexStringToLong(String)}. We use 64-bit longs to conform to the
 * Google Dapper paper
 * (see <a href="http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf">http://static.googleusercontent.com/media/research.google.com/en/us/pubs/archive/36356.pdf</a>)
 * and unsigned hex encoding to conform to the ZipKin distributed tracing B3 implementation
 * (see <a href="http://zipkin.io/pages/instrumenting.html">http://zipkin.io/pages/instrumenting.html</a>).
 *
 * @author Nic Munroe
 */
public class TraceAndSpanIdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TraceAndSpanIdGenerator.class);

    /**
     * The random number generator this class will use to generate random numbers. Since this is used in {@link #generateId()} and we want those numbers to span the full
     * set of 64 bit possibilities this should be a {@link SecureRandom} if at all possible (see {@link java.util.Random#nextLong()} for details on the default
     * Random's limitations here). And since it will be used in a multithreaded and time sensitive environment it should not be a blocking {@link SecureRandom} instance
     * (i.e. it should be a pseudorandom number generator, not a true random number generator which might need to block for entropy data).
     */
    private static final Random random = getRandomInstance("SHA1PRNG");

    /**
     * Intentionally private constructor to force all access via static methods.
     */
    private TraceAndSpanIdGenerator() {
        // Do nothing
    }

    /**
     * @return A newly-generated random 64-bit long encoded as a String <b>UNSIGNED AND IN HEX FORMAT</b> - intended for use as a trace or span ID.
     *          The returned string will have a length of 16 characters (zeroes will be prepended as padding if necessary to
     *          reach a string length of 16). The hex format is necessary to be fully B3 compatible and therefore fully Zipkin compatible
     *          (see <a href="http://zipkin.io/pages/instrumenting.html">http://zipkin.io/pages/instrumenting.html</a>). If you simply want a full 64-bit
     *          random normal *signed* Java long encoded in *decimal* format for other reasons then you can call {@link String#valueOf(long)} and pass in a
     *          long created by {@link #generate64BitRandomLong()}.
     *          <p>You can convert the unsigned hex-encoded longs returned by this method back into normal Java long primitives by passing them to
     *          {@link #unsignedLowerHexStringToLong(String)}.
     */
    public static String generateId() {
        return longToUnsignedLowerHexString(generate64BitRandomLong());
    }

    /**
     * @return A random long pulled from the full 64-bit random search space (as opposed to the 48 bits of randomness you get from
     *          {@link java.util.Random#nextLong()}).
     */
    public static long generate64BitRandomLong() {
        byte[] random8Bytes = new byte[8];
        random.nextBytes(random8Bytes);

        return convertBytesToLong(random8Bytes);
    }

    /**
     * @param primitiveLong The long value that should be converted to an unsigned long encoded in a hexadecimal string.
     * @return The given long value converted to an unsigned hex encoded string of length 16 (zeroes will be prepended as padding if necessary to
     *          reach a string length of 16). You can convert back to a Java long primitive by passing the result into
     *          {@link #unsignedLowerHexStringToLong(String)}.
     */
    public static String longToUnsignedLowerHexString(long primitiveLong) {
        return ZipkinHexHelpers.toLowerHex(primitiveLong);
    }

    /**
     * @param hexString The lowercase hexadecimal string representing an unsigned 64-bit long that you want to convert to a Java long primitive.
     * @return The Java long primitive represented by the given lowercase hex string. If the string isn't lowercase hexadecimal encoded or if
     *          the resulting long wouldn't fit inside 64 bits then a {@link NumberFormatException} will be thrown.
     */
    public static long unsignedLowerHexStringToLong(String hexString) {
        return ZipkinHexHelpers.lowerHexToUnsignedLong(hexString);
    }

    /**
     * Converts the given 8 bytes to a long value. Implementation for this taken from {@link java.util.UUID#UUID(byte[])}.
     */
    protected static long convertBytesToLong(byte[] byteArray) {
        if (byteArray.length != 8)
            throw new IllegalArgumentException("byteArray must be 8 bytes in length");

        long longVal = 0;
        for (int i=0; i<8; i++)
            longVal = (longVal << 8) | (byteArray[i] & 0xff);

        return longVal;
    }

    /**
     * Tries to retrieve and return the {@link SecureRandom} with the given implementation using {@link SecureRandom#getInstance(String)}, and falls back to a
     * {@code new Random(System.nanoTime())} if that instance could not be found.
     */
    protected static Random getRandomInstance(String desiredSecureRandomImplementation) {
        Random randomToUse;

        try {
            randomToUse = SecureRandom.getInstance(desiredSecureRandomImplementation);
            randomToUse.setSeed(System.nanoTime());
        } catch (NoSuchAlgorithmException e) {
            logger.error("Unable to retrieve the {} SecureRandom instance. Defaulting to a new Random(System.nanoTime()) instead. NOTE: This means random longs will not cover " +
                    "the full 64 bits of possible values! See the javadocs for Random.nextLong() for details. dtracer_error=true", desiredSecureRandomImplementation,  e);
            randomToUse = new Random(System.nanoTime());
        }

        return randomToUse;
    }

    /**
     * <p>
     *     The code in this class came from the Zipkin repository v1.11.1
     *     (https://github.com/openzipkin/zipkin/blob/1.11.1/zipkin/src/main/java/zipkin/internal/Util.java)
     *     and licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
     * </p>
     */
    protected static class ZipkinHexHelpers {

        private ZipkinHexHelpers() {
            // Do nothing
        }

        /**
         * Parses a 1 to 32 character lower-hex string with no prefix into an unsigned long, tossing any
         * bits higher than 64.
         */
        public static long lowerHexToUnsignedLong(String lowerHex) {
            int length = lowerHex.length();
            if (length < 1 || length > 32) throw isntLowerHexLong(lowerHex);

            // trim off any high bits
            int i = length > 16 ? length - 16 : 0;

            long result = 0;
            for (; i < length; i++) {
                char c = lowerHex.charAt(i);
                result <<= 4;
                if (c >= '0' && c <= '9') {
                    result |= c - '0';
                } else if (c >= 'a' && c <= 'f') {
                    result |= c - 'a' + 10;
                } else {
                    throw isntLowerHexLong(lowerHex);
                }
            }
            return result;
        }

        static NumberFormatException isntLowerHexLong(String lowerHex) {
            return new NumberFormatException(
                lowerHex + " should be a 1 to 32 character lower-hex string with no prefix");
        }

        /** Inspired by {@code okio.Buffer.writeLong} */
        static String toLowerHex(long v) {
            char[] data = new char[16];
            writeHexLong(data, 0, v);
            return new String(data);
        }

        /** Inspired by {@code okio.Buffer.writeLong} */
        static void writeHexLong(char[] data, int pos, long v) {
            writeHexByte(data, pos + 0,  (byte) ((v >>> 56L) & 0xff));
            writeHexByte(data, pos + 2,  (byte) ((v >>> 48L) & 0xff));
            writeHexByte(data, pos + 4,  (byte) ((v >>> 40L) & 0xff));
            writeHexByte(data, pos + 6,  (byte) ((v >>> 32L) & 0xff));
            writeHexByte(data, pos + 8,  (byte) ((v >>> 24L) & 0xff));
            writeHexByte(data, pos + 10, (byte) ((v >>> 16L) & 0xff));
            writeHexByte(data, pos + 12, (byte) ((v >>> 8L) & 0xff));
            writeHexByte(data, pos + 14, (byte)  (v & 0xff));
        }

        static final char[] HEX_DIGITS =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        static void writeHexByte(char[] data, int pos, byte b) {
            data[pos + 0] = HEX_DIGITS[(b >> 4) & 0xf];
            data[pos + 1] = HEX_DIGITS[b & 0xf];
        }
    }
}
