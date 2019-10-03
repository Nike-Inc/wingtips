package notcomponentscanned.componenttestoverridedefaultconverter;

import com.nike.wingtips.springboot2.webflux.zipkin2.WingtipsWithZipkinSpringBoot2WebfluxConfiguration;
import com.nike.wingtips.zipkin2.util.WingtipsToZipkinSpanConverter;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBoot2WebfluxConfiguration.class)
@SuppressWarnings("unused")
public class ComponentTestMainWithConverterOverride {

    public static final WingtipsToZipkinSpanConverter CUSTOM_CONVERTER_INSTANCE = mock(WingtipsToZipkinSpanConverter.class);

    @Bean
    public WingtipsToZipkinSpanConverter customConverter() {
        return CUSTOM_CONVERTER_INSTANCE;
    }

}
