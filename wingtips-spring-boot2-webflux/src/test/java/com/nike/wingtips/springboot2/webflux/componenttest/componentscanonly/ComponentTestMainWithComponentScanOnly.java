package com.nike.wingtips.springboot2.webflux.componenttest.componentscanonly;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@ComponentScan(basePackages = "com.nike")
@PropertySource("classpath:/wingtipsAndReactorSupportEnabled.properties")
public class ComponentTestMainWithComponentScanOnly {

    public ComponentTestMainWithComponentScanOnly() {
    }

}
