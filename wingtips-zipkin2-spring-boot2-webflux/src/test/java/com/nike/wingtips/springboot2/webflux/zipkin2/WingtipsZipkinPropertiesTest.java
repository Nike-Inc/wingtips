package com.nike.wingtips.springboot2.webflux.zipkin2;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the functionality of {@link WingtipsZipkinProperties}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsZipkinPropertiesTest {

    private WingtipsZipkinProperties props;

    @Before
    public void beforeMethod() {
        props = new WingtipsZipkinProperties();
    }

    @DataProvider(value = {
        "true   |   true",
        "TRUE   |   true",
        "tRuE   |   true",
        "false  |   false",
        "FALSE  |   false",
        "fAlSe  |   false",
        "       |   false",
        "junk   |   false",
        "null   |   false",
    }, splitBy = "\\|")
    @Test
    public void zipkinDisabled_getter_and_setter_works_as_expected(
        String propValueAsStringForSetter, boolean expectedGetterResult
    ) {
        // when
        props.setZipkinDisabled(propValueAsStringForSetter);

        // then
        assertThat(props.isZipkinDisabled()).isEqualTo(expectedGetterResult);
    }

    @Test
    public void exercise_standard_getters_and_setters() {
        // baseUrl getter/setter
        {
            String nonNullBaseUrl = UUID.randomUUID().toString();
            props.setBaseUrl(nonNullBaseUrl);
            assertThat(props.getBaseUrl()).isEqualTo(nonNullBaseUrl);

            props.setBaseUrl(null);
            assertThat(props.getBaseUrl()).isNull();
        }

        // serviceName getter/setter
        {
            String nonNullServiceName = UUID.randomUUID().toString();
            props.setServiceName(nonNullServiceName);
            assertThat(props.getServiceName()).isEqualTo(nonNullServiceName);

            props.setServiceName(null);
            assertThat(props.getServiceName()).isNull();
        }
    }

    @DataProvider(value = {
        "true   |   true    |   true    |   false",
        "true   |   true    |   false   |   false",

        "true   |   false   |   true    |   false",
        "true   |   false   |   false   |   false",
        
        "false  |   true    |   true    |   false",
        "false  |   true    |   false   |   false",

        "false  |   false   |   true    |   false",
        "false  |   false   |   false   |   true" // The one case where expectedResult is true
    }, splitBy = "\\|")
    @Test
    public void shouldApplyWingtipsToZipkinLifecycleListener_works_as_expected(
        boolean zipkinDisabled, boolean baseUrlIsNull, boolean serviceNameIsNull,
        boolean expectedResult
    ) {
        // given
        String baseUrl = (baseUrlIsNull) ? null : UUID.randomUUID().toString();
        String serviceName = (serviceNameIsNull) ? null : UUID.randomUUID().toString();

        props.setZipkinDisabled(String.valueOf(zipkinDisabled));
        props.setBaseUrl(baseUrl);
        props.setServiceName(serviceName);

        // when
        boolean result = props.shouldApplyWingtipsToZipkinLifecycleListener();

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

}
