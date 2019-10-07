package notcomponentscanned.componenttest;

import com.nike.wingtips.spring.webflux.server.WingtipsSpringWebfluxWebFilter;
import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsSpringBoot2WebfluxConfiguration.class)
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithCustomWingtipsWebFilter {

    public static final WingtipsSpringWebfluxWebFilter customFilter = new WingtipsSpringWebfluxWebFilter();

    @Bean
    @SuppressWarnings("unused")
    public WingtipsSpringWebfluxWebFilter customFilter() {
        return customFilter;
    }

}
