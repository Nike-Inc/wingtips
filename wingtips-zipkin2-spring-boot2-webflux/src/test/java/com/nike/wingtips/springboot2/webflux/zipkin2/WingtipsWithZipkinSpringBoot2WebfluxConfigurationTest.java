package com.nike.wingtips.springboot2.webflux.zipkin2;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;
import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxProperties;
import com.nike.wingtips.springboot2.webflux.zipkin2.WingtipsWithZipkinSpringBoot2WebfluxConfiguration.DefaultOverrides;
import com.nike.wingtips.springboot2.webflux.zipkin2.componenttest.componentscanonly.ComponentTestMainWithComponentScanOnly;
import com.nike.wingtips.springboot2.webflux.zipkin2.componenttest.manualimportandcomponentscan.ComponentTestMainWithBothManualImportAndComponentScan;
import com.nike.wingtips.springboot2.webflux.zipkin2.componenttest.manualimportonly.ComponentTestMainManualImportOnly;
import com.nike.wingtips.zipkin2.WingtipsToZipkinLifecycleListener;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverterDefaultImpl;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import notcomponentscanned.componenttestoverridebothreporterandconverter.ComponentTestMainWithReporterAndConverterOverrides;
import notcomponentscanned.componenttestoverridedefaultconverter.ComponentTestMainWithConverterOverride;
import notcomponentscanned.componenttestoverridedefaultreporter.ComponentTestMainWithReporterOverride;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link WingtipsWithZipkinSpringBoot2WebfluxConfiguration}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsWithZipkinSpringBoot2WebfluxConfigurationTest {

    @Before
    public void beforeMethod() {
        clearTracerSpanLifecycleListeners();
    }

    @After
    public void afterMethod() {
        clearTracerSpanLifecycleListeners();
    }

    private void clearTracerSpanLifecycleListeners() {
        Tracer.getInstance().removeAllSpanLifecycleListeners();
    }

    @SuppressWarnings("SameParameterValue")
    private WingtipsZipkinProperties generateProps(boolean disabled,
                                                   String baseUrl,
                                                   String serviceName) {
        WingtipsZipkinProperties props = new WingtipsZipkinProperties();
        props.setZipkinDisabled(String.valueOf(disabled));
        props.setBaseUrl(baseUrl);
        props.setServiceName(serviceName);
        return props;
    }

    @SuppressWarnings({"unused", "unchecked"})
    private enum DefaultOverridesScenario {
        NULL_DEFAULT_OVERRIDES(null),
        NO_OVERRIDES(new DefaultOverrides()),
        WITH_REPORTER_OVERRIDE(defaultOverridesWithMockReporter()),
        WITH_CONVERTER_OVERRIDE(defaultOverridesWithMockConverter()),
        WITH_REPORTER_AND_CONVERTER_OVERRIDE(defaultOverridesWithMocks());

        public final DefaultOverrides defaultOverrides;

        DefaultOverridesScenario(
            DefaultOverrides defaultOverrides) {
            this.defaultOverrides = defaultOverrides;
        }

        private static DefaultOverrides defaultOverridesWithMockReporter() {
            DefaultOverrides defaultOverrides = new DefaultOverrides();
            defaultOverrides.zipkinReporter = mock(Reporter.class);
            return defaultOverrides;
        }

        private static DefaultOverrides defaultOverridesWithMockConverter() {
            DefaultOverrides defaultOverrides = new DefaultOverrides();
            defaultOverrides.zipkinSpanConverter = mock(WingtipsToZipkinSpanConverter.class);
            return defaultOverrides;
        }

        private static DefaultOverrides defaultOverridesWithMocks() {
            DefaultOverrides defaultOverrides = new DefaultOverrides();
            defaultOverrides.zipkinReporter = mock(Reporter.class);
            defaultOverrides.zipkinSpanConverter = mock(WingtipsToZipkinSpanConverter.class);
            return defaultOverrides;
        }
    }

    @DataProvider(value = {
        "NULL_DEFAULT_OVERRIDES",
        "NO_OVERRIDES",
        "WITH_REPORTER_OVERRIDE",
        "WITH_CONVERTER_OVERRIDE",
        "WITH_REPORTER_AND_CONVERTER_OVERRIDE"
    })
    @Test
    public void constructor_registers_WingtipsToZipkinLifecycleListener_with_expected_values(
        DefaultOverridesScenario scenario
    ) throws MalformedURLException {
        // given
        String baseUrl = "http://localhost:4242/" + UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        WingtipsZipkinProperties props = generateProps(false, baseUrl, serviceName);

        // when
        WingtipsWithZipkinSpringBoot2WebfluxConfiguration config =
            new WingtipsWithZipkinSpringBoot2WebfluxConfiguration(props, scenario.defaultOverrides);

        // then
        assertThat(config.wingtipsZipkinProperties).isSameAs(props);

        List<SpanLifecycleListener> listeners = Tracer.getInstance().getSpanLifecycleListeners();
        assertThat(listeners).hasSize(1);
        assertThat(listeners.get(0)).isInstanceOf(WingtipsToZipkinLifecycleListener.class);
        WingtipsToZipkinLifecycleListener listener = (WingtipsToZipkinLifecycleListener) listeners.get(0);

        assertThat(Whitebox.getInternalState(listener, "serviceName")).isEqualTo(serviceName);
        assertThat(Whitebox.getInternalState(listener, "zipkinEndpoint"))
            .isEqualTo(Endpoint.newBuilder().serviceName(serviceName).build());
        assertThat(Whitebox.getInternalState(listener, "zipkinSpanConverter")).isNotNull();

        Object zipkinSpanReporter = Whitebox.getInternalState(listener, "zipkinSpanReporter");
        Object zipkinSpanConverter = Whitebox.getInternalState(listener, "zipkinSpanConverter");

        if (scenario.defaultOverrides != null) {
            assertThat(config.zipkinReporterOverride).isSameAs(scenario.defaultOverrides.zipkinReporter);
            assertThat(config.zipkinSpanConverterOverride).isSameAs(scenario.defaultOverrides.zipkinSpanConverter);

            if (scenario.defaultOverrides.zipkinReporter != null) {
                assertThat(zipkinSpanReporter).isSameAs(scenario.defaultOverrides.zipkinReporter);
            }

            if (scenario.defaultOverrides.zipkinSpanConverter != null) {
                assertThat(zipkinSpanConverter).isSameAs(scenario.defaultOverrides.zipkinSpanConverter);
            }
        }

        if (scenario.defaultOverrides == null || scenario.defaultOverrides.zipkinReporter == null) {
            assertThat(zipkinSpanReporter).isInstanceOf(AsyncReporter.class);
            Object spanSender = Whitebox.getInternalState(zipkinSpanReporter, "sender");
            assertThat(spanSender).isInstanceOf(URLConnectionSender.class);
            assertThat(Whitebox.getInternalState(spanSender, "endpoint"))
                .isEqualTo(new URL(baseUrl + "/api/v2/spans"));

            assertThat(config.zipkinReporterOverride).isNull();
        }
        
        if (scenario.defaultOverrides == null || scenario.defaultOverrides.zipkinSpanConverter == null) {
            assertThat(zipkinSpanConverter).isInstanceOf(WingtipsToZipkinSpanConverterDefaultImpl.class);
            assertThat(config.zipkinSpanConverterOverride).isNull();
        }
    }

    @Test
    public void constructor_does_not_register_WingtipsToZipkinLifecycleListener_when_props_shouldApplyWingtipsToZipkinLifecycleListener_returns_false() {
        // given
        WingtipsZipkinProperties props = mock(WingtipsZipkinProperties.class);
        doReturn(false).when(props).shouldApplyWingtipsToZipkinLifecycleListener();

        // when
        new WingtipsWithZipkinSpringBoot2WebfluxConfiguration(props, null);

        // then
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).isEmpty();
        verify(props).shouldApplyWingtipsToZipkinLifecycleListener();
        verifyNoMoreInteractions(props);
    }

    @SuppressWarnings("unused")
    private enum ComponentTestSetup {
        MANUAL_IMPORT_ONLY(ComponentTestMainManualImportOnly.class, false, null, null),
        COMPONENT_SCAN_ONLY(ComponentTestMainWithComponentScanOnly.class, true, null, null),
        BOTH_MANUAL_AND_COMPONENT_SCAN(ComponentTestMainWithBothManualImportAndComponentScan.class, true, null, null),
        WITH_ZIPKIN_REPORTER_OVERRIDE(
            ComponentTestMainWithReporterOverride.class,
            false,
            ComponentTestMainWithReporterOverride.CUSTOM_REPORTER_INSTANCE,
            null
        ),
        WITH_CONVERTER_OVERRIDE(
            ComponentTestMainWithConverterOverride.class,
            false,
            null,
            ComponentTestMainWithConverterOverride.CUSTOM_CONVERTER_INSTANCE
        ),
        WITH_BOTH_REPORTER_AND_CONVERTER_OVERRIDES(
            ComponentTestMainWithReporterAndConverterOverrides.class,
            false,
            ComponentTestMainWithReporterOverride.CUSTOM_REPORTER_INSTANCE,
            ComponentTestMainWithConverterOverride.CUSTOM_CONVERTER_INSTANCE
        );

        final boolean expectComponentScannedObjects;
        final Class<?> mainClass;
        final Reporter<zipkin2.Span> expectedReporterOverride;
        final WingtipsToZipkinSpanConverter expectedConverterOverride;

        ComponentTestSetup(Class<?> mainClass,
                           boolean expectComponentScannedObjects,
                           Reporter<Span> expectedReporterOverride,
                           WingtipsToZipkinSpanConverter expectedConverterOverride) {
            this.mainClass = mainClass;
            this.expectComponentScannedObjects = expectComponentScannedObjects;
            this.expectedReporterOverride = expectedReporterOverride;
            this.expectedConverterOverride = expectedConverterOverride;
        }
    }

    // This component test verifies that a Spring Boot application successfully utilizes WingtipsSpringBoot2WebfluxConfiguration
    //      and WingtipsSpringBoot2WebfluxProperties when it is component scanned, imported manually, or both. Specifically
    //      we should not get multiple bean definition errors even when WingtipsSpringBoot2WebfluxConfiguration is *both*
    //      component scanned *and* imported manually.
    // We also test that app-specific overrides of certain things are honored/used (e.g. Zipkin Reporter).
    @DataProvider(value = {
        "MANUAL_IMPORT_ONLY",
        "COMPONENT_SCAN_ONLY",
        "BOTH_MANUAL_AND_COMPONENT_SCAN",
        "WITH_ZIPKIN_REPORTER_OVERRIDE",
        "WITH_CONVERTER_OVERRIDE",
        "WITH_BOTH_REPORTER_AND_CONVERTER_OVERRIDES"
    })
    @Test
    public void component_test(ComponentTestSetup componentTestSetup) {
        // given
        int serverPort = findFreePort();
        Class<?> mainClass = componentTestSetup.mainClass;

        ConfigurableApplicationContext serverAppContext = SpringApplication.run(mainClass, "--server.port=" + serverPort);

        try {
            // when
            WingtipsSpringBoot2WebfluxConfiguration
                baseConfig = serverAppContext.getBean(WingtipsSpringBoot2WebfluxConfiguration.class);
            WingtipsWithZipkinSpringBoot2WebfluxConfiguration zipkinConfig =
                serverAppContext.getBean(WingtipsWithZipkinSpringBoot2WebfluxConfiguration.class);
            WingtipsSpringBoot2WebfluxProperties
                baseProps = serverAppContext.getBean(WingtipsSpringBoot2WebfluxProperties.class);
            WingtipsZipkinProperties zipkinProps = serverAppContext.getBean(WingtipsZipkinProperties.class);

            String[] someComponentScannedClassBeanNames =
                serverAppContext.getBeanNamesForType(SomeComponentScannedClass.class);

            List<SpanLifecycleListener> lifecycleListeners = Tracer.getInstance().getSpanLifecycleListeners();

            // then
            // Sanity check that we component scanned (or not) as appropriate.
            if (componentTestSetup.expectComponentScannedObjects) {
                assertThat(someComponentScannedClassBeanNames).isNotEmpty();
            }
            else {
                assertThat(someComponentScannedClassBeanNames).isEmpty();
            }

            // WingtipsSpringBoot2WebfluxConfiguration, WingtipsWithZipkinSpringBoot2WebfluxConfiguration,
            //      WingtipsSpringBoot2WebfluxProperties, and WingtipsZipkinProperties should be available as beans, and
            //      the base config should use the same props we received.
            assertThat(baseConfig).isNotNull();
            assertThat(baseProps).isNotNull();
            assertThat(baseConfig).extracting("wingtipsProperties")
                .containsExactly(baseProps);

            assertThat(zipkinConfig).isNotNull();
            assertThat(zipkinProps).isNotNull();

            // Verify that a WingtipsToZipkinLifecycleListener was registered with Tracer.
            assertThat(lifecycleListeners).hasSize(1);
            SpanLifecycleListener listener = lifecycleListeners.get(0);
            assertThat(listener).isInstanceOf(WingtipsToZipkinLifecycleListener.class);

            // Verify the Zipkin Reporter override if expected.
            if (componentTestSetup.expectedReporterOverride != null) {
                assertThat(Whitebox.getInternalState(listener, "zipkinSpanReporter"))
                    .isSameAs(ComponentTestMainWithReporterOverride.CUSTOM_REPORTER_INSTANCE);
            }

            // Verify the Wingtips-to-Zipkin converter override if expected.
            if (componentTestSetup.expectedConverterOverride != null) {
                assertThat(Whitebox.getInternalState(listener, "zipkinSpanConverter"))
                    .isSameAs(ComponentTestMainWithConverterOverride.CUSTOM_CONVERTER_INSTANCE);
            }
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