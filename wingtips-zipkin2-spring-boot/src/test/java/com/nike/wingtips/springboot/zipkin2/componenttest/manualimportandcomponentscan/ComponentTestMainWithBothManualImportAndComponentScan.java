package com.nike.wingtips.springboot.zipkin2.componenttest.manualimportandcomponentscan;

import com.nike.wingtips.springboot.zipkin2.WingtipsWithZipkinSpringBootConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBootConfiguration.class)
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithBothManualImportAndComponentScan {

    public ComponentTestMainWithBothManualImportAndComponentScan() {
    }

}
