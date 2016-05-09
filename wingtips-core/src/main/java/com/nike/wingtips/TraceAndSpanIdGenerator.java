package com.nike.wingtips;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

/**
 * ID generation class for use with trace IDs, span IDs, and parent span IDs for the {@link Span} class. Call the static {@link #generateId()} method whenever you need to create
 * a new ID. These IDs are returned as strings but they can be parsed by {@link Long#parseLong(String)} into long values. We use longs to conform to the Google Dapper paper and
 * Twitter ZipKin distributed tracing implementation.
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
     * @return A newly-generated random 64-bit long as a String.
     */
    public static String generateId() {
        byte[] random8Bytes = new byte[8];
        random.nextBytes(random8Bytes);

        long randomLong = convertBytesToLong(random8Bytes);

        return String.valueOf(randomLong);
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
}
