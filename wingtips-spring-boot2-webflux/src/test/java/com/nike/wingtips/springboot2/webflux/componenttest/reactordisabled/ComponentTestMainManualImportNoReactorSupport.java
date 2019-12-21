package com.nike.wingtips.springboot2.webflux.componenttest.reactordisabled;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:/wingtipsAndReactorSupportDisabled.properties")
public class ComponentTestMainManualImportNoReactorSupport {

    public ComponentTestMainManualImportNoReactorSupport() {
    }

}
