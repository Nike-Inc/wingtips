package com.nike.wingtips;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests the functionality of {@link TraceAndSpanIdGenerator}
 */
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
    public void generateId_should_return_string_that_can_be_parsed_into_a_long() {
        // given: String ID value generated by generateId()
        String idVal = TraceAndSpanIdGenerator.generateId();

        // when: that ID is parsed into a long
        Long idAsLong = Long.parseLong(idVal);

        // then: it doesn't explode
        assertThat(idAsLong).isNotNull();
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
}
