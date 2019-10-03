package notcomponentscanned.componenttestoverridedefaultreporter;

import com.nike.wingtips.springboot2.webflux.zipkin2.WingtipsWithZipkinSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import zipkin2.reporter.Reporter;

import static org.mockito.Mockito.mock;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBoot2WebfluxConfiguration.class)
@SuppressWarnings("unused")
public class ComponentTestMainWithReporterOverride {

    @SuppressWarnings("unchecked")
    public static final Reporter<zipkin2.Span> CUSTOM_REPORTER_INSTANCE = mock(Reporter.class);

    @Bean
    public Reporter<zipkin2.Span> customZipkinReporter() {
        return CUSTOM_REPORTER_INSTANCE;
    }

}
