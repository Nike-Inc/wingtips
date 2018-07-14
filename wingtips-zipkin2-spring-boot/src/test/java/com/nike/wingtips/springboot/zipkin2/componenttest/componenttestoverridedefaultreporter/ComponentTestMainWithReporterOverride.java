package com.nike.wingtips.springboot.zipkin2.componenttest.componenttestoverridedefaultreporter;

import com.nike.wingtips.springboot.zipkin2.WingtipsWithZipkinSpringBootConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import zipkin2.reporter.Reporter;

import static org.mockito.Mockito.mock;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBootConfiguration.class)
public class ComponentTestMainWithReporterOverride {

    public static final Reporter<zipkin2.Span> CUSTOM_REPORTER_INSTANCE = mock(Reporter.class);

    @Bean
    public Reporter<zipkin2.Span> customZipkinReporter() {
        return CUSTOM_REPORTER_INSTANCE;
    }

}
