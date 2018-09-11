package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer.SpanLoggingRepresentation;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the functionality of {@link WingtipsSpringBootProperties}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringBootPropertiesTest {

    private WingtipsSpringBootProperties props;

    @Before
    public void beforeMethod() {
        props = new WingtipsSpringBootProperties();
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
    public void wingtipsDisabled_getter_and_setter_works_as_expected(
        String propValueAsStringForSetter, boolean expectedGetterResult
    ) {
        // when
        props.setWingtipsDisabled(propValueAsStringForSetter);

        // then
        assertThat(props.isWingtipsDisabled()).isEqualTo(expectedGetterResult);
    }

    @Test
    public void exercise_standard_getters_and_setters() {
        // userIdHeaderKeys getter/setter
        {
            String nonNullKey = UUID.randomUUID().toString();
            props.setUserIdHeaderKeys(nonNullKey);
            assertThat(props.getUserIdHeaderKeys()).isEqualTo(nonNullKey);

            props.setUserIdHeaderKeys(null);
            assertThat(props.getUserIdHeaderKeys()).isNull();
        }

        // spanLoggingFormat getter/setter
        {
            for (SpanLoggingRepresentation format : SpanLoggingRepresentation.values()) {
                props.setSpanLoggingFormat(format);
                assertThat(props.getSpanLoggingFormat()).isEqualTo(format);
            }

            props.setSpanLoggingFormat(null);
            assertThat(props.getSpanLoggingFormat()).isNull();
        }
        
        // tagStrategy getter/setter
        {
            props.setServerSideSpanTaggingStrategy("OPENTRACING");
            assertThat(props.getServerSideSpanTaggingStrategy()).isEqualTo("OPENTRACING");

            props.setServerSideSpanTaggingStrategy(null);
            assertThat(props.getServerSideSpanTaggingStrategy()).isNull();
        }
    }

}