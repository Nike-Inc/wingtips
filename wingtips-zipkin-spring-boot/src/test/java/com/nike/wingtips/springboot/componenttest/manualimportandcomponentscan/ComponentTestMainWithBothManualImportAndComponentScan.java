package com.nike.wingtips.springboot.componenttest.manualimportandcomponentscan;

import com.nike.wingtips.springboot.WingtipsWithZipkinSpringBootConfiguration;

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
