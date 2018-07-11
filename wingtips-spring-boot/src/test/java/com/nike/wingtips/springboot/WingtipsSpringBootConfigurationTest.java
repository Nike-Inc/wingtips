package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.Tracer.SpanLoggingRepresentation;
import com.nike.wingtips.servlet.RequestTracingFilter;
import com.nike.wingtips.springboot.WingtipsSpringBootConfiguration.DoNothingServletFilter;
import com.nike.wingtips.springboot.componenttest.componentscanonly.ComponentTestMainWithComponentScanOnly;
import com.nike.wingtips.springboot.componenttest.manualimportandcomponentscan.ComponentTestMainWithBothManualImportAndComponentScan;
import com.nike.wingtips.springboot.componenttest.manualimportonly.ComponentTestMainManualImportOnly;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import static com.nike.wingtips.servlet.RequestTracingFilter.USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests the functionality of {@link WingtipsSpringBootConfiguration}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringBootConfigurationTest {

    private WingtipsSpringBootProperties generateProps(boolean disabled,
                                                       String userIdHeaderKeys,
                                                       SpanLoggingRepresentation spanLoggingFormat) {
        WingtipsSpringBootProperties props = new WingtipsSpringBootProperties();
        props.setWingtipsDisabled(String.valueOf(disabled));
        props.setUserIdHeaderKeys(userIdHeaderKeys);
        props.setSpanLoggingFormat(spanLoggingFormat);
        return props;
    }

    @DataProvider(value = {
        "JSON",
        "KEY_VALUE",
        "null"
    })
    @Test
    public void constructor_works_as_expected(SpanLoggingRepresentation spanLoggingFormat) {
        // given
        WingtipsSpringBootProperties props = generateProps(false, UUID.randomUUID().toString(), spanLoggingFormat);
        SpanLoggingRepresentation existingSpanLoggingFormat = Tracer.getInstance().getSpanLoggingRepresentation();
        SpanLoggingRepresentation expectedSpanLoggingFormat = (spanLoggingFormat == null)
                                                              ? existingSpanLoggingFormat
                                                              : spanLoggingFormat;

        // when
        WingtipsSpringBootConfiguration conf = new WingtipsSpringBootConfiguration(props);

        // then
        assertThat(conf.wingtipsProperties).isSameAs(props);
        assertThat(Tracer.getInstance().getSpanLoggingRepresentation()).isEqualTo(expectedSpanLoggingFormat);
    }

    @DataProvider(value = {
        "true   |   true",
        "true   |   false",
        "false  |   true",
        "false  |   false"
    }, splitBy = "\\|")
    @Test
    public void wingtipsRequestTracingFilter_returns_FilterRegistrationBean_with_expected_values(
        boolean appFilterOverrideIsNull, boolean userIdHeaderKeysIsNull
    ) {
        // given
        RequestTracingFilter appFilterOverride = (appFilterOverrideIsNull) ? null : mock(RequestTracingFilter.class);
        String userIdHeaderKeys = (userIdHeaderKeysIsNull) ? null : UUID.randomUUID().toString();

        WingtipsSpringBootProperties props = generateProps(false, userIdHeaderKeys, null);
        WingtipsSpringBootConfiguration conf = new WingtipsSpringBootConfiguration(props);
        conf.requestTracingFilter = appFilterOverride;

        // when
        FilterRegistrationBean filterRegistrationBean = conf.wingtipsRequestTracingFilter();

        // then
        if (appFilterOverride == null) {
            assertThat(filterRegistrationBean.getFilter())
                .isNotNull()
                .isInstanceOf(RequestTracingFilter.class);
        }
        else {
            assertThat(filterRegistrationBean.getFilter()).isSameAs(appFilterOverride);
        }

        String userIdHeaderKeysFilterInitParam =
            filterRegistrationBean.getInitParameters().get(USER_ID_HEADER_KEYS_LIST_INIT_PARAM_NAME);

        if (userIdHeaderKeys == null) {
            assertThat(userIdHeaderKeysFilterInitParam).isNull();
        }
        else {
            assertThat(userIdHeaderKeysFilterInitParam).isEqualTo(userIdHeaderKeys);
        }

        assertThat(filterRegistrationBean.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    public void wingtipsRequestTracingFilter_returns_DoNothingServletFilter_if_WingtipsSpringBootProperties_indicates_disabled() {
        // given
        WingtipsSpringBootProperties props = generateProps(true, null, null);
        WingtipsSpringBootConfiguration conf = new WingtipsSpringBootConfiguration(props);

        // when
        FilterRegistrationBean filterRegistrationBean = conf.wingtipsRequestTracingFilter();

        // then
        assertThat(filterRegistrationBean.getFilter())
            .isNotNull()
            .isInstanceOf(DoNothingServletFilter.class);
    }

    @Test
    public void DoNothingServletFilter_works_as_expected() throws IOException, ServletException {
        // given
        DoNothingServletFilter dnsf = new DoNothingServletFilter();
        ServletRequest requestMock = mock(ServletRequest.class);
        ServletResponse responseMock = mock(ServletResponse.class);
        FilterChain filterChainMock = mock(FilterChain.class);
        FilterConfig filterConfigMock = mock(FilterConfig.class);

        // when
        dnsf.doFilter(requestMock, responseMock, filterChainMock);

        // then
        verify(filterChainMock).doFilter(requestMock, responseMock);
        verifyNoMoreInteractions(filterChainMock);
        verifyZeroInteractions(requestMock, responseMock);

        // and when
        dnsf.init(filterConfigMock);

        // then
        verifyZeroInteractions(filterConfigMock);

        // and when
        dnsf.destroy();
        
        // then
        verifyNoMoreInteractions(requestMock, responseMock, filterChainMock, filterConfigMock);
    }

    private enum ComponentTestSetup {
        MANUAL_IMPORT_ONLY(ComponentTestMainManualImportOnly.class, false),
        COMPONENT_SCAN_ONLY(ComponentTestMainWithComponentScanOnly.class, true),
        BOTH_MANUAL_AND_COMPONENT_SCAN(ComponentTestMainWithBothManualImportAndComponentScan.class, true);

        final boolean expectComponentScannedObjects;
        final Class<?> mainClass;

        ComponentTestSetup(Class<?> mainClass, boolean expectComponentScannedObjects) {
            this.mainClass = mainClass;
            this.expectComponentScannedObjects = expectComponentScannedObjects;
        }
    }

    // This component test verifies that a Spring Boot application successfully utilizes WingtipsSpringBootConfiguration
    //      and WingtipsSpringBootProperties when it is component scanned, imported manually, or both. Specifically
    //      we should not get multiple bean definition errors even when WingtipsSpringBootConfiguration is *both*
    //      component scanned *and* imported manually.
    @DataProvider(value = {
        "MANUAL_IMPORT_ONLY",
        "COMPONENT_SCAN_ONLY",
        "BOTH_MANUAL_AND_COMPONENT_SCAN"
    })
    @Test
    public void component_test(ComponentTestSetup componentTestSetup) {
        // given
        int serverPort = findFreePort();
        Class<?> mainClass = componentTestSetup.mainClass;

        ConfigurableApplicationContext serverAppContext = SpringApplication.run(mainClass, "--server.port=" + serverPort);

        try {
            // when
            WingtipsSpringBootConfiguration config = serverAppContext.getBean(WingtipsSpringBootConfiguration.class);
            WingtipsSpringBootProperties props = serverAppContext.getBean(WingtipsSpringBootProperties.class);
            String[] someComponentScannedClassBeanNames =
                serverAppContext.getBeanNamesForType(SomeComponentScannedClass.class);

            // then
            // Sanity check that we component scanned (or not) as appropriate.
            if (componentTestSetup.expectComponentScannedObjects) {
                assertThat(someComponentScannedClassBeanNames).isNotEmpty();
            }
            else {
                assertThat(someComponentScannedClassBeanNames).isEmpty();
            }

            // WingtipsSpringBootConfiguration and WingtipsSpringBootProperties should be available as beans, and
            //      the config should use the same props we received.
            assertThat(config).isNotNull();
            assertThat(props).isNotNull();
            assertThat(config.wingtipsProperties).isSameAs(props);
        }
        finally {
            SpringApplication.exit(serverAppContext);
        }
    }

    private static int findFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Component
    private static class SomeComponentScannedClass {
    }

}