package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.lifecyclelistener.SpanLifecycleListener;
import com.nike.wingtips.zipkin.WingtipsToZipkinLifecycleListener;
import com.nike.wingtips.zipkin.util.ZipkinSpanSenderDefaultHttpImpl;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the functionality of {@link WingtipsWithZipkinSpringBootConfiguration}.
 *
 * @author Nic Munroe
 */
@RunWith(DataProviderRunner.class)
public class WingtipsWithZipkinSpringBootConfigurationTest {

    @Before
    public void beforeMethod() {
        clearTracerSpanLifecycleListeners();
    }

    @After
    public void afterMethod() {
        clearTracerSpanLifecycleListeners();
    }

    private void clearTracerSpanLifecycleListeners() {
        List<SpanLifecycleListener> listeners = new ArrayList<>(Tracer.getInstance().getSpanLifecycleListeners());
        for (SpanLifecycleListener listener : listeners) {
            Tracer.getInstance().removeSpanLifecycleListener(listener);
        }
    }

    private WingtipsZipkinProperties generateProps(boolean disabled,
                                                   String baseUrl,
                                                   String serviceName,
                                                   String localComponentNamespace) {
        WingtipsZipkinProperties props = new WingtipsZipkinProperties();
        props.setZipkinDisabled(String.valueOf(disabled));
        props.setBaseUrl(baseUrl);
        props.setServiceName(serviceName);
        props.setLocalComponentNamespace(localComponentNamespace);
        return props;
    }

    @Test
    public void constructor_registers_WingtipsToZipkinLifecycleListener() {
        // given
        String baseUrl = "http://localhost:4242/" + UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String localComponentNamespace = UUID.randomUUID().toString();
        WingtipsZipkinProperties props = generateProps(false, baseUrl, serviceName, localComponentNamespace);

        // when
        new WingtipsWithZipkinSpringBootConfiguration(props);

        // then
        List<SpanLifecycleListener> listeners = Tracer.getInstance().getSpanLifecycleListeners();
        assertThat(listeners).hasSize(1);
        assertThat(listeners.get(0)).isInstanceOf(WingtipsToZipkinLifecycleListener.class);
        WingtipsToZipkinLifecycleListener listener = (WingtipsToZipkinLifecycleListener) listeners.get(0);
        Object zipkinSpanSender = Whitebox.getInternalState(listener, "zipkinSpanSender");
        assertThat(zipkinSpanSender).isInstanceOf(ZipkinSpanSenderDefaultHttpImpl.class);
        assertThat(Whitebox.getInternalState(zipkinSpanSender, "postZipkinSpansUrl").toString())
            .isEqualTo(baseUrl + "/api/v1/spans");
        assertThat(Whitebox.getInternalState(listener, "serviceName")).isEqualTo(serviceName);
        assertThat(Whitebox.getInternalState(listener, "localComponentNamespace"))
            .isEqualTo(localComponentNamespace);
    }

    @Test
    public void constructor_does_not_register_WingtipsToZipkinLifecycleListener_when_props_shouldApplyWingtipsToZipkinLifecycleListener_returns_false() {
        // given
        WingtipsZipkinProperties props = mock(WingtipsZipkinProperties.class);
        doReturn(false).when(props).shouldApplyWingtipsToZipkinLifecycleListener();

        // when
        new WingtipsWithZipkinSpringBootConfiguration(props);

        // then
        assertThat(Tracer.getInstance().getSpanLifecycleListeners()).isEmpty();
        verify(props).shouldApplyWingtipsToZipkinLifecycleListener();
        verifyNoMoreInteractions(props);
    }

}