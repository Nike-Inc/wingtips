package notcomponentscanned.componenttestoverridebothreporterandconverter;

import com.nike.wingtips.springboot2.webflux.zipkin2.WingtipsWithZipkinSpringBoot2WebfluxConfiguration;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import notcomponentscanned.componenttestoverridedefaultconverter.ComponentTestMainWithConverterOverride;
import notcomponentscanned.componenttestoverridedefaultreporter.ComponentTestMainWithReporterOverride;
import zipkin2.reporter.Reporter;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBoot2WebfluxConfiguration.class)
@SuppressWarnings("unused")
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
