package com.nike.wingtips.springboot.zipkin2.componenttest.componentscanonly;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithComponentScanOnly {

    public ComponentTestMainWithComponentScanOnly() {
    }

}
