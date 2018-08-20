package com.nike.wingtips.springboot.zipkin2.componenttest.componenttestoverridebothreporterandconverter;

import com.nike.wingtips.springboot.zipkin2.WingtipsWithZipkinSpringBootConfiguration;
import com.nike.wingtips.springboot.zipkin2.componenttest.componenttestoverridedefaultconverter.ComponentTestMainWithConverterOverride;
import com.nike.wingtips.springboot.zipkin2.componenttest.componenttestoverridedefaultreporter.ComponentTestMainWithReporterOverride;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import zipkin2.reporter.Reporter;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBootConfiguration.class)
public class ComponentTestMainWithReporterAndConverterOverrides {

    @Bean
    public Reporter<zipkin2.Span> customZipkinReporter() {
        return ComponentTestMainWithReporterOverride.CUSTOM_REPORTER_INSTANCE;
    }

    @Bean
    public WingtipsToZipkinSpanConverter customConverter() {
        return ComponentTestMainWithConverterOverride.CUSTOM_CONVERTER_INSTANCE;
    }
    
}
