package com.nike.wingtips;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests the functionality of {@link TraceAndSpanIdGenerator}
 */
@RunWith(DataProviderRunner.class)
public class TraceAndSpanIdGeneratorTest {

    @Test
    public void constructor_is_private() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<TraceAndSpanIdGenerator> defaultConstructor = TraceAndSpanIdGenerator.class.getDeclaredConstructor();
        Exception caughtException = null;
        try {
            defaultConstructor.newInstance();
        }
        catch (Exception ex) {
            caughtException = ex;
        }

        assertThat(caughtException).isNotNull();
        assertThat(caughtException).isInstanceOf(IllegalAccessException.class);

        // Set the constructor to accessible and create one. Why? Code coverage. :p
        defaultConstructor.setAccessible(true);
        TraceAndSpanIdGenerator instance = defaultConstructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    public void constructor_for_ZipkinHexHelpers_is_private() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<TraceAndSpanIdGenerator.ZipkinHexHelpers> defaultConstructor = TraceAndSpanIdGenerator.ZipkinHexHelpers.class.getDeclaredConstructor();
        Exception caughtException = null;
        try {
            defaultConstructor.newInstance();
        }
        catch (Exception ex) {
            caughtException = ex;
        }

        assertThat(caughtException).isNotNull();
        assertThat(caughtException).isInstanceOf(IllegalAccessException.class);

        // Set the constructor to accessible and create one. Why? Code coverage. :p
        defaultConstructor.setAccessible(true);
        TraceAndSpanIdGenerator.ZipkinHexHelpers instance = defaultConstructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    public void getRandomInstance_should_return_requested_instance_if_available() {
        // given: all available SecureRandom algorithm providers
        Provider[] availableProviders = Security.getProviders();
        assertThat(availableProviders).isNotNull();
        assertThat(availableProviders.length).isGreaterThanOrEqualTo(1);

        // Ignore some algorithms because they fail on OSX if you're not an admin.
        // We don't care that *all* algorithms are available at all times on all platforms, just that the code does what it's supposed to do.
        List<String> excludedAlgorithms = Collections.singletonList("NativePRNGNonBlocking");

        Set<String> availableSecureRandomAlgorithms = new HashSet<>();

        for (Provider provider : availableProviders) {
            Set<Provider.Service> services = provider.getServices();
            for (Provider.Service service : services) {
                String type = service.getType();
                if ("SecureRandom".equals(type) && !excludedAlgorithms.contains(service.getAlgorithm()))
                    availableSecureRandomAlgorithms.add(service.getAlgorithm());
            }
        }

        assertThat(availableSecureRandomAlgorithms).isNotEmpty();

        for (String algorithm : availableSecureRandomAlgorithms) {
            // when: we ask getRandomInstance for the algorithm
            Random randomGenerator = TraceAndSpanIdGenerator.getRandomInstance(algorithm);

            // then: we get back a valid SecureRandom that uses the requested algorithm
            assertThat(randomGenerator).isNotNull();
            assertThat(randomGenerator).isInstanceOf(SecureRandom.class);
            //noinspection ConstantConditions
            assertThat(((SecureRandom)randomGenerator).getAlgorithm()).isEqualTo(algorithm);
        }
    }

    @Test
    public void getRandomInstance_should_default_to_normal_random_if_desired_algorithm_is_not_available() {
        Random randomGenerator = TraceAndSpanIdGenerator.getRandomInstance("QuantumDoohickey");
        assertThat(randomGenerator).isNotNull();
        assertThat(randomGenerator).isNotInstanceOf(SecureRandom.class);
    }

    @Test
    public void convertBytesToLong_should_work_correctly_for_known_value() {
        // given: byte[] that maps to known long value
        final long EXPECTED_LONG_VALUE = 4242424242L;
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / 8);
        buffer.putLong(EXPECTED_LONG_VALUE);
        byte[] longAsByteArray = buffer.array();
        assertThat(longAsByteArray.length).isEqualTo(8);

        // when: convertBytesToLong() is called
        long returnVal = TraceAndSpanIdGenerator.convertBytesToLong(longAsByteArray);

        // then: the return value is what we expect
        assertThat(returnVal).isEqualTo(EXPECTED_LONG_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertBytesToLong_should_explode_if_byte_array_is_less_than_8_bytes() {
        byte[] badByteArray = new byte[]{0, 0, 0, 0, 0, 0, 1};
        assertThat(badByteArray.length).isLessThan(8);
        TraceAndSpanIdGenerator.convertBytesToLong(badByteArray);
        fail("Expected IllegalArgumentException but none was thrown");
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertBytesToLong_should_explode_if_byte_array_is_more_than_8_bytes() {
        byte[] badByteArray = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 1};
        assertThat(badByteArray.length).isGreaterThan(8);
        TraceAndSpanIdGenerator.convertBytesToLong(badByteArray);
        fail("Expected IllegalArgumentException but none was thrown");
    }

    @Test
    public void generateId_should_return_16_char_length_string_that_can_be_parsed_into_a_long_when_interpreted_as_a_64_bit_unsigned_hex_long() {
        Set<Character> charactersFromIds = new HashSet<>();

        for (int i = 0; i < 10000; i++) {
            // given: String ID value generated by generateId()
            final String idVal = TraceAndSpanIdGenerator.generateId();

            // then: that ID has 16 characters and can be interpreted as a 64 bit unsigned hex long and parsed into a Java long primitive
            assertThat(idVal).hasSize(16);
            Throwable parseException = catchThrowable(new ThrowableAssert.ThrowingCallable() {
                @Override
                public void call() throws Throwable {
                    new BigInteger(idVal, 16).longValue();
                }
            });
            assertThat(parseException).isNull();

            // Store the characters we see in this ID in a set so we can verify later that we've only ever seen hex-compatible characters.
            for (char c : idVal.toCharArray()) {
                charactersFromIds.add(c);
            }
        }

        // We should have only run into lowercase hex characters, and given how many IDs we generated we should have hit all the lowercase hex characters.
        assertThat(charactersFromIds).containsOnly('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f');
    }

    @Test
    public void generateId_should_not_generate_duplicate_ids_over_reasonable_number_of_attempts() throws Exception {
        // given
        Set<String> ids = new HashSet<>();
        int numAttempts = 1000000;

        // when
        for (int i = 0; i < numAttempts; i++) {
            ids.add(TraceAndSpanIdGenerator.generateId());
        }

        // then
        assertThat(ids.size()).isEqualTo(numAttempts);
    }

    @Test
    public void generate64BitRandomLong_should_not_generate_duplicate_ids_over_reasonable_number_of_attempts() throws Exception {
        // given
        Set<Long> randomLongs = new HashSet<>();
        int numAttempts = 1000000;

        // when
        for (int i = 0; i < numAttempts; i++) {
            randomLongs.add(TraceAndSpanIdGenerator.generate64BitRandomLong());
        }

        // then
        assertThat(randomLongs.size()).isEqualTo(numAttempts);
    }

    @DataProvider(value = {
        "0000000000000000   |   0",
        "0000000000000001   |   1",
        "ffffffffffffffff   |   18446744073709551615",
        "fffffffffffffffe   |   18446744073709551614",
        "7fae59489091369a   |   9200389256962455194",
        "eb5e7aaefeb92b4f   |   16960128138740312911",
        "d2153abe4c047408   |   15138070311469347848",
        "9041ee0d07d6c72c   |   10394851154681317164",
        "6470a5ce0e9262f4   |   7237466905610707700",
        "000003c8a251fb93   |   4160251624339",
    }, splitBy = "\\|")
    @Test
    public void longToUnsignedLowerHexString_and_unsignedLowerHexStringToLong_work_as_expected_for_known_values(String actualHexValue, String actualUnsignedDecimalValue) {
        // given
        long actualSignedPrimitive = new BigInteger(actualUnsignedDecimalValue).longValue();

        // when
        String calculatedHexValue = TraceAndSpanIdGenerator.longToUnsignedLowerHexString(actualSignedPrimitive);
        long calculatedPrimitiveValue = TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(actualHexValue);

        // then
        assertThat(calculatedHexValue).isEqualTo(actualHexValue);
        assertThat(calculatedPrimitiveValue).isEqualTo(actualSignedPrimitive);
    }

    @DataProvider(value = {
        "                                      ", // less than 16 chars
        "123e4567-e89b-12d3-a456-426655440000  ", // UUID format (hyphens and also >32 chars)
        "/                                     ", // before '0' char
        ":                                     ", // after '9' char
        "`                                     ", // before 'a' char
        "g                                     ", // after 'f' char
        "ABCDEF                                "  // uppercase hex chars
    }, splitBy = "\\|")
    @Test
    public void unsignedLowerHexStringToLong_throws_NumberFormatException_for_illegal_args(final String badHexString) {
        // when selecting right-most 16 characters
        Throwable ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(badHexString);
            }
        });

        // then
        assertThat(ex).isInstanceOf(NumberFormatException.class);

        // when selecting 16 characters at offset 0
        ex = catchThrowable(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                TraceAndSpanIdGenerator.unsignedLowerHexStringToLong(badHexString, 0);
            }
        });

        // then
        assertThat(ex).isInstanceOf(NumberFormatException.class);
    }
}
