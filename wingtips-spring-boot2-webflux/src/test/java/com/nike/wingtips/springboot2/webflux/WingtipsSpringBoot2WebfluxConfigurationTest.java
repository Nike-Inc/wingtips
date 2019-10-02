package com.nike.wingtips.springboot2.webflux;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.Tracer.SpanLoggingRepresentation;
import com.nike.wingtips.spring.webflux.server.SpringWebfluxServerRequestTagAdapter;
import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.springboot2.webflux.componenttest.componentscanonly.ComponentTestMainWithComponentScanOnly;
import com.nike.wingtips.springboot2.webflux.componenttest.manualimportandcomponentscan.ComponentTestMainWithBothManualImportAndComponentScan;
import com.nike.wingtips.springboot2.webflux.componenttest.manualimportonly.ComponentTestMainManualImportOnly;
import com.nike.wingtips.tags.HttpTagAndSpanNamingAdapter;
import com.nike.wingtips.tags.HttpTagAndSpanNamingStrategy;
import com.nike.wingtips.tags.NoOpHttpTagStrategy;
import com.nike.wingtips.tags.OpenTracingHttpTagStrategy;
import com.nike.wingtips.tags.ZipkinHttpTagStrategy;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import notcomponentscanned.componenttest.ComponentTestMainWithCustomWingtipsWebFilter;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests the functionality of {@link WingtipsSpringBoot2WebfluxConfiguration}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsSpringBoot2WebfluxConfigurationTest {

    private WingtipsSpringBoot2WebfluxProperties generateProps(
        boolean disabled,
        String userIdHeaderKeys,
        SpanLoggingRepresentation spanLoggingFormat,
        String tagAndNamingStrategy,
        String tagAndNamingAdapter
    ) {
        WingtipsSpringBoot2WebfluxProperties props = new WingtipsSpringBoot2WebfluxProperties();
        props.setWingtipsDisabled(String.valueOf(disabled));
        props.setUserIdHeaderKeys(userIdHeaderKeys);
        props.setSpanLoggingFormat(spanLoggingFormat);
        props.setServerSideSpanTaggingStrategy(tagAndNamingStrategy);
        props.setServerSideSpanTaggingAdapter(tagAndNamingAdapter);
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
        WingtipsSpringBoot2WebfluxProperties props = generateProps(
            false, UUID.randomUUID().toString(), spanLoggingFormat, "someTagStrategy", "someTagAdapter"
        );
        SpanLoggingRepresentation existingSpanLoggingFormat = Tracer.getInstance().getSpanLoggingRepresentation();
        SpanLoggingRepresentation expectedSpanLoggingFormat = (spanLoggingFormat == null)
                                                              ? existingSpanLoggingFormat
                                                              : spanLoggingFormat;

        // when
        WingtipsSpringBoot2WebfluxConfiguration conf = new WingtipsSpringBoot2WebfluxConfiguration(props);

        // then
        assertThat(conf.wingtipsProperties).isSameAs(props);
        assertThat(Tracer.getInstance().getSpanLoggingRepresentation()).isEqualTo(expectedSpanLoggingFormat);
    }

    @SuppressWarnings("unused")
    private enum PropertiesScenario {
        USER_ID_HEADER_KEYS_PROP_IS_SET(nonNullUserIdHeaderKeysProp(), null, null),
        TAG_AND_NAMING_STRATEGY_PROP_IS_SET(null, nonNullStrategyProp(), null),
        TAG_AND_NAMING_ADAPTER_PROP_IS_SET(null, null, nonNullAdapterProp()),
        ALL_PROPS_ARE_SET(nonNullUserIdHeaderKeysProp(), nonNullStrategyProp(), nonNullAdapterProp());

        public final String userIdHeaderKeys;
        public final String tagAndNamingStrategy;
        public final String tagAndNamingAdapter;

        PropertiesScenario(String userIdHeaderKeys, String tagAndNamingStrategy, String tagAndNamingAdapter) {
            this.userIdHeaderKeys = userIdHeaderKeys;
            this.tagAndNamingStrategy = tagAndNamingStrategy;
            this.tagAndNamingAdapter = tagAndNamingAdapter;
        }

        private static String nonNullUserIdHeaderKeysProp() {
            return "user-id-hk-1-" + UUID.randomUUID().toString() + ",user-id-hk-2-" + UUID.randomUUID().toString();
        }

        private static String nonNullStrategyProp() {
            return CustomTagStrategy.class.getName();
        }

        private static String nonNullAdapterProp() {
            return CustomTagAdapter.class.getName();
        }

        private List<String> getExpectedUserIdHeaderKeyList() {
            if (userIdHeaderKeys == null) {
                return Collections.emptyList();
            }

            return Stream.of(userIdHeaderKeys.split(",")).collect(Collectors.toList());
        }

        private Class<?> getExpectedTagStrategyClass() {
            if (tagAndNamingStrategy == null) {
                return ZipkinHttpTagStrategy.class;
            }

            return CustomTagStrategy.class;
        }

        private Class<?> getExpectedTagAdapterClass() {
            if (tagAndNamingAdapter == null) {
                return SpringWebfluxServerRequestTagAdapter.class;
            }

            return CustomTagAdapter.class;
        }
    }

    @SuppressWarnings("WeakerAccess")
    static class CustomTagStrategy extends ZipkinHttpTagStrategy<ServerWebExchange, ServerHttpResponse> {}

    @SuppressWarnings("WeakerAccess")
    static class CustomTagAdapter extends SpringWebfluxServerRequestTagAdapter {}

    @DataProvider(value = {
        "true   |   USER_ID_HEADER_KEYS_PROP_IS_SET",
        "true   |   TAG_AND_NAMING_STRATEGY_PROP_IS_SET",
        "true   |   TAG_AND_NAMING_ADAPTER_PROP_IS_SET",
        "true   |   ALL_PROPS_ARE_SET",
        "false  |   USER_ID_HEADER_KEYS_PROP_IS_SET",
        "false  |   TAG_AND_NAMING_STRATEGY_PROP_IS_SET",
        "false  |   TAG_AND_NAMING_ADAPTER_PROP_IS_SET",
        "false  |   ALL_PROPS_ARE_SET"
    }, splitBy = "\\|")
    @SuppressWarnings("unchecked")
    @Test
    public void wingtipsRequestTracingFilter_returns_WingtipsSpringWebfluxWebFilter_with_expected_values(
        boolean appFilterOverrideIsNull, PropertiesScenario scenario
    ) {
        // given
        WingtipsSpringWebfluxWebFilter appFilterOverride = (appFilterOverrideIsNull)
                                                           ? null
                                                           : mock(WingtipsSpringWebfluxWebFilter.class);

        WingtipsSpringBoot2WebfluxProperties props = generateProps(
            false, scenario.userIdHeaderKeys, null, scenario.tagAndNamingStrategy, scenario.tagAndNamingAdapter
        );
        WingtipsSpringBoot2WebfluxConfiguration conf = new WingtipsSpringBoot2WebfluxConfiguration(props);
        conf.customSpringWebfluxWebFilter = appFilterOverride;

        // when
        WingtipsSpringWebfluxWebFilter filterBean = conf.wingtipsSpringWebfluxWebFilter();

        // then
        if (appFilterOverride == null) {
            assertThat(filterBean)
                .isNotNull()
                .isInstanceOf(WingtipsSpringWebfluxWebFilter.class);

            List<String> filterBeanUserIdHeaderKeys =
                (List<String>) Whitebox.getInternalState(filterBean, "userIdHeaderKeys");
            HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> filterBeanTagStrategy =
                (HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse>)
                    Whitebox.getInternalState(filterBean, "tagAndNamingStrategy");
            HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> filterBeanTagAdapter =
                (HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse>)
                    Whitebox.getInternalState(filterBean, "tagAndNamingAdapter");

            assertThat(filterBeanUserIdHeaderKeys).isEqualTo(scenario.getExpectedUserIdHeaderKeyList());
            assertThat(filterBeanTagStrategy).isInstanceOf(scenario.getExpectedTagStrategyClass());
            assertThat(filterBeanTagAdapter).isInstanceOf(scenario.getExpectedTagAdapterClass());

            assertThat(filterBean.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }
        else {
            assertThat(filterBean).isNull();
        }
    }

    @Test
    public void wingtipsRequestTracingFilter_returns_null_if_WingtipsSpringBootProperties_indicates_disabled() {
        // given
        WingtipsSpringBoot2WebfluxProperties props = generateProps(true, null, null, null, null);
        WingtipsSpringBoot2WebfluxConfiguration conf = new WingtipsSpringBoot2WebfluxConfiguration(props);

        // expect
        assertThat(conf.wingtipsSpringWebfluxWebFilter()).isNull();
    }

    private enum ExtractUserIdHeaderKeysScenario {
        NULL_HEADERS_STRING(null, null),
        EMPTY_HEADERS_STRING("", Collections.emptyList()),
        BLANK_HEADERS_STRING("  \n\r\t  ", Collections.emptyList()),
        SINGLE_VALUE_HEADERS_STRING("foo", singletonList("foo")),
        MULTIPLE_VALUE_HEADERS_STRING("foo,bar", Arrays.asList("foo", "bar")),
        MULTIPLE_VALUES_WITH_BLANK_AND_EMPTY_ENTRIES(
            "foo, bar,  baz  ,,  , \r\n\t ,blah",
            Arrays.asList("foo", "bar", "baz", "blah")
        );

        public final String userIdHeaderKeysString;
        public final List<String> expectedResult;

        ExtractUserIdHeaderKeysScenario(String userIdHeaderKeysString, List<String> expectedResult) {
            this.userIdHeaderKeysString = userIdHeaderKeysString;
            this.expectedResult = expectedResult;
        }
    }

    @DataProvider
    public static List<List<ExtractUserIdHeaderKeysScenario>> extractUserIdHeaderKeysScenarioDataProvider() {
        return Stream.of(ExtractUserIdHeaderKeysScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("extractUserIdHeaderKeysScenarioDataProvider")
    @Test
    public void extractUserIdHeaderKeysAsList_works_as_expected(
        ExtractUserIdHeaderKeysScenario scenario
    ) {
        // given
        WingtipsSpringBoot2WebfluxProperties props = generateProps(
            false, scenario.userIdHeaderKeysString, null, null, null
        );
        WingtipsSpringBoot2WebfluxConfiguration conf = new WingtipsSpringBoot2WebfluxConfiguration(props);

        // when
        List<String> result = conf.extractUserIdHeaderKeysAsList(props);

        // then
        assertThat(result).isEqualTo(scenario.expectedResult);
    }

    @SuppressWarnings("unused")
    private enum ExtractTagAndNamingStrategyScenario {
        NULL_STRATEGY_NAME(null, null, null),
        EMPTY_STRATEGY_NAME(null, null, null),
        BLANK_STRATEGY_NAME(null, null, null),
        ZIPKIN_ALL_CAPS_SHORTNAME(
            "ZIPKIN", ZipkinHttpTagStrategy.class, ZipkinHttpTagStrategy.getDefaultInstance()
        ),
        ZIPKIN_LOWERCASE_SHORTNAME(
            "zipkin", ZipkinHttpTagStrategy.class, ZipkinHttpTagStrategy.getDefaultInstance()
        ),
        ZIPKIN_MIXED_CASE_SHORTNAME(
            "zIpKiN", ZipkinHttpTagStrategy.class, ZipkinHttpTagStrategy.getDefaultInstance()
        ),
        OT_ALL_CAPS_SHORTNAME(
            "OPENTRACING", OpenTracingHttpTagStrategy.class, OpenTracingHttpTagStrategy.getDefaultInstance()
        ),
        OT_LOWERCASE_SHORTNAME(
            "opentracing", OpenTracingHttpTagStrategy.class, OpenTracingHttpTagStrategy.getDefaultInstance()
        ),
        OT_MIXED_CASE_SHORTNAME(
            "oPeNtRaCiNg", OpenTracingHttpTagStrategy.class, OpenTracingHttpTagStrategy.getDefaultInstance()
        ),
        NONE_ALL_CAPS_SHORTNAME(
            "NONE", NoOpHttpTagStrategy.class, NoOpHttpTagStrategy.getDefaultInstance()
        ),
        NONE_LOWERCASE_SHORTNAME(
            "none", NoOpHttpTagStrategy.class, NoOpHttpTagStrategy.getDefaultInstance()
        ),
        NONE_MIXED_CASE_SHORTNAME(
            "nOnE", NoOpHttpTagStrategy.class, NoOpHttpTagStrategy.getDefaultInstance()
        ),
        NOOP_ALL_CAPS_SHORTNAME(
            "NOOP", NoOpHttpTagStrategy.class, NoOpHttpTagStrategy.getDefaultInstance()
        ),
        NOOP_LOWERCASE_SHORTNAME(
            "noop", NoOpHttpTagStrategy.class, NoOpHttpTagStrategy.getDefaultInstance()
        ),
        NOOP_MIXED_CASE_SHORTNAME(
            "nOoP", NoOpHttpTagStrategy.class, NoOpHttpTagStrategy.getDefaultInstance()
        ),
        CUSTOM_BY_CLASSNAME(
            CustomTagStrategy.class.getName(), CustomTagStrategy.class, null
        ),
        INVALID_CLASSNAME(
            UUID.randomUUID().toString(), null, null
        );

        public final String strategyName;
        public final Class<? extends HttpTagAndSpanNamingStrategy> expectedResultClass;
        public final HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> expectedExactMatch;

        ExtractTagAndNamingStrategyScenario(
            String strategyName,
            Class<? extends HttpTagAndSpanNamingStrategy> expectedResultClass,
            HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> expectedExactMatch
        ) {
            this.strategyName = strategyName;
            this.expectedResultClass = expectedResultClass;
            this.expectedExactMatch = expectedExactMatch;
        }
    }

    @DataProvider
    public static List<List<ExtractTagAndNamingStrategyScenario>> extractTagAndNamingStrategyScenarioDataProvider() {
        return Stream.of(ExtractTagAndNamingStrategyScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("extractTagAndNamingStrategyScenarioDataProvider")
    @Test
    public void extractTagAndNamingStrategy_works_as_expected(
        ExtractTagAndNamingStrategyScenario scenario
    ) {
        // given
        WingtipsSpringBoot2WebfluxProperties props = generateProps(
            false, null, null, scenario.strategyName, null
        );
        WingtipsSpringBoot2WebfluxConfiguration conf = new WingtipsSpringBoot2WebfluxConfiguration(props);

        // when
        HttpTagAndSpanNamingStrategy<ServerWebExchange, ServerHttpResponse> result =
            conf.extractTagAndNamingStrategy(props);

        // then
        if (scenario.expectedResultClass == null) {
            assertThat(result).isNull();
        }
        else {
            assertThat(result).isNotNull();
            assertThat(result.getClass()).isEqualTo(scenario.expectedResultClass);
        }

        if (scenario.expectedExactMatch != null) {
            assertThat(result).isSameAs(scenario.expectedExactMatch);
        }
    }

    @SuppressWarnings("unused")
    private enum ExtractTagAndNamingAdapterScenario {
        NULL_ADAPTER_NAME(null, null),
        EMPTY_ADAPTER_NAME(null, null),
        BLANK_ADAPTER_NAME(null, null),
        CUSTOM_BY_CLASSNAME(
            CustomTagAdapter.class.getName(), CustomTagAdapter.class
        ),
        INVALID_CLASSNAME(
            UUID.randomUUID().toString(), null
        );

        public final String adapterName;
        public final Class<? extends HttpTagAndSpanNamingAdapter> expectedResultClass;

        ExtractTagAndNamingAdapterScenario(
            String adapterName,
            Class<? extends HttpTagAndSpanNamingAdapter> expectedResultClass
        ) {
            this.adapterName = adapterName;
            this.expectedResultClass = expectedResultClass;
        }
    }

    @DataProvider
    public static List<List<ExtractTagAndNamingAdapterScenario>> extractTagAndNamingAdapterScenarioDataProvider() {
        return Stream.of(ExtractTagAndNamingAdapterScenario.values())
                     .map(Collections::singletonList)
                     .collect(Collectors.toList());
    }

    @UseDataProvider("extractTagAndNamingAdapterScenarioDataProvider")
    @Test
    public void extractTagAndNamingAdapter_works_as_expected(
        ExtractTagAndNamingAdapterScenario scenario
    ) {
        // given
        WingtipsSpringBoot2WebfluxProperties props = generateProps(
            false, null, null, null, scenario.adapterName
        );
        WingtipsSpringBoot2WebfluxConfiguration conf = new WingtipsSpringBoot2WebfluxConfiguration(props);

        // when
        HttpTagAndSpanNamingAdapter<ServerWebExchange, ServerHttpResponse> result =
            conf.extractTagAndNamingAdapter(props);

        // then
        if (scenario.expectedResultClass == null) {
            assertThat(result).isNull();
        }
        else {
            assertThat(result).isNotNull();
            assertThat(result.getClass()).isEqualTo(scenario.expectedResultClass);
        }
    }

    @SuppressWarnings("unused")
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

    // This component test verifies that a Spring Boot application successfully utilizes
    //      WingtipsSpringBoot2WebfluxConfiguration and WingtipsSpringBoot2WebfluxProperties when it is component
    //      scanned, imported manually, or both. Specifically we should not get multiple bean definition errors even
    //      when WingtipsSpringBoot2WebfluxConfiguration is *both* component scanned *and* imported manually.
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
            WingtipsSpringBoot2WebfluxConfiguration
                config = serverAppContext.getBean(WingtipsSpringBoot2WebfluxConfiguration.class);
            WingtipsSpringBoot2WebfluxProperties props = serverAppContext.getBean(WingtipsSpringBoot2WebfluxProperties.class);
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

            // WingtipsSpringBoot2WebfluxConfiguration and WingtipsSpringBoot2WebfluxProperties should be available as
            //      beans, and the config should use the same props we received.
            assertThat(config).isNotNull();
            assertThat(props).isNotNull();
            assertThat(config.wingtipsProperties).isSameAs(props);

            // The config should not have any custom WingtipsSpringWebfluxWebFilter. Spring will populate
            //      the config.customSpringWebfluxWebFilter field with whatever wingtipsSpringWebfluxWebFilter()
            //      produces - so they should be the same.
            Map<String, WingtipsSpringWebfluxWebFilter> filtersFromSpring =
                serverAppContext.getBeansOfType(WingtipsSpringWebfluxWebFilter.class);
            assertThat(filtersFromSpring).isEqualTo(
                Collections.singletonMap("wingtipsSpringWebfluxWebFilter", config.customSpringWebfluxWebFilter)
            );
        }
        finally {
            SpringApplication.exit(serverAppContext);
        }
    }

    @Test
    public void component_test_with_custom_WingtipsSpringWebfluxWebFilter() {
        // given
        int serverPort = findFreePort();

        ConfigurableApplicationContext serverAppContext = SpringApplication.run(
            ComponentTestMainWithCustomWingtipsWebFilter.class,
            "--server.port=" + serverPort
        );

        try {
            // when
            WingtipsSpringBoot2WebfluxConfiguration
                config = serverAppContext.getBean(WingtipsSpringBoot2WebfluxConfiguration.class);
            WingtipsSpringBoot2WebfluxProperties props = serverAppContext.getBean(WingtipsSpringBoot2WebfluxProperties.class);
            String[] someComponentScannedClassBeanNames =
                serverAppContext.getBeanNamesForType(SomeComponentScannedClass.class);

            // then
            // Sanity check that we component scanned (or not) as appropriate. This particular component test does
            //      include component scanning.
            assertThat(someComponentScannedClassBeanNames).isNotEmpty();

            // WingtipsSpringBoot2WebfluxConfiguration and WingtipsSpringBoot2WebfluxProperties should be available as
            //      beans, and the config should use the same props we received.
            assertThat(config).isNotNull();
            assertThat(props).isNotNull();
            assertThat(config.wingtipsProperties).isSameAs(props);

            // Finally, the thing this test is verifying: the config's custom filter should be the same one from the
            //      component test main class, and it should be the one that Spring exposes.
            assertThat(config.customSpringWebfluxWebFilter)
                .isSameAs(ComponentTestMainWithCustomWingtipsWebFilter.customFilter);
            Map<String, WingtipsSpringWebfluxWebFilter> filtersFromSpring =
                serverAppContext.getBeansOfType(WingtipsSpringWebfluxWebFilter.class);
            assertThat(filtersFromSpring).isEqualTo(
                Collections.singletonMap("customFilter", ComponentTestMainWithCustomWingtipsWebFilter.customFilter)
            );
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