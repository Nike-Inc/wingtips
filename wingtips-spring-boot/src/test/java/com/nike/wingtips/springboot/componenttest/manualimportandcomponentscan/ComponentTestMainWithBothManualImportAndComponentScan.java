package com.nike.wingtips.springboot.componenttest.manualimportandcomponentscan;

import com.nike.wingtips.springboot.WingtipsSpringBootConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsSpringBootConfiguration.class)
@ComponentScan(basePackages = "com.nike")
public class ComponentTestMainWithBothManualImportAndComponentScan {

    public ComponentTestMainWithBothManualImportAndComponentScan() {
    }

}
