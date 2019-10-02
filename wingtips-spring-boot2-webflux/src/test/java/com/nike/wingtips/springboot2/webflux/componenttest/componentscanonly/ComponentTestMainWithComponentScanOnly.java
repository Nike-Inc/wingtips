package com.nike.wingtips.springboot2.webflux.componenttest.componentscanonly;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithComponentScanOnly {

    public ComponentTestMainWithComponentScanOnly() {
    }

}
