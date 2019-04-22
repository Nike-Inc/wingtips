package com.nike.wingtips.lightstep;

import com.nike.wingtips.Span;
import com.nike.wingtips.Tracer;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

public class WingtipsToLightStepLifecycleListenerTest {

    private String mockAccessToken, mockSatelliteUrl, mockServiceName;
    private int mockSatellitePort;
    private WingtipsToLightStepLifecycleListener listener;
    private Span mockSpan;

    @Before
    public void beforeMethod() {
        mockAccessToken = "test-access-token";
        mockSatelliteUrl = "test-satellite-url";
        mockServiceName = "test-service-name";
        mockSatellitePort = 8080;
        listener = new WingtipsToLightStepLifecycleListener(mockServiceName, mockAccessToken, mockSatelliteUrl, mockSatellitePort);
    }

    @Test
    public void spanStarted_test() {
        Span mockSpan = Tracer.getInstance().startRequestWithRootSpan("test-span-name");
        mockSpan.close();
        listener.spanSampled(mockSpan);
    }

    @Test
    public void spanSampled_test() {
        Span mockSpan = Tracer.getInstance().startRequestWithRootSpan("test-span-name");
        mockSpan.close();
        listener.spanSampled(mockSpan);
    }

    @Test
    public void spanCompleted_test() {
        Span mockSpan = Tracer.getInstance().startRequestWithRootSpan("test-span-name");
        mockSpan.close();
        listener.spanCompleted(mockSpan);
    }

    @Test
    public void constructor_test() {
        WingtipsToLightStepLifecycleListener testListner = new WingtipsToLightStepLifecycleListener(
                mockServiceName, mockAccessToken, mockSatelliteUrl, mockSatellitePort);
        assertEquals(mockServiceName, testListner.serviceName);
        assertEquals(mockAccessToken, testListner.accessToken);
        assertEquals(mockSatelliteUrl, testListner.satelliteUrl);
        assertEquals(mockSatellitePort, testListner.satellitePort);
    }

}