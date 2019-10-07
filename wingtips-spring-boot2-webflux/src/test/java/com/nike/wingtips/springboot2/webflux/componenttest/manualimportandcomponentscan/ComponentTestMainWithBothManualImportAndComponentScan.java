package com.nike.wingtips.springboot2.webflux.componenttest.manualimportandcomponentscan;

import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsSpringBoot2WebfluxConfiguration.class)
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithBothManualImportAndComponentScan {

    public ComponentTestMainWithBothManualImportAndComponentScan() {
    }

}
