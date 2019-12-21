package com.nike.wingtips.springboot2.webflux.componenttest.manualimportonly;

import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@Import(WingtipsSpringBoot2WebfluxConfiguration.class)
@PropertySource("classpath:/wingtipsAndReactorSupportEnabled.properties")
public class ComponentTestMainManualImportOnly {

    public ComponentTestMainManualImportOnly() {
    }

}
