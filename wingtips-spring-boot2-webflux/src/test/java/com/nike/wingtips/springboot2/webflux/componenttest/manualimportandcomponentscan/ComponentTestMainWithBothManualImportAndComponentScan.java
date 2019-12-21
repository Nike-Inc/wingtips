package com.nike.wingtips.springboot2.webflux.componenttest.manualimportandcomponentscan;

import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@Import(WingtipsSpringBoot2WebfluxConfiguration.class)
@PropertySource("classpath:/wingtipsAndReactorSupportEnabled.properties")
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithBothManualImportAndComponentScan {

    public ComponentTestMainWithBothManualImportAndComponentScan() {
    }

}
