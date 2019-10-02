package com.nike.wingtips.springboot2.webflux.componenttest.manualimportonly;

import com.nike.wingtips.springboot2.webflux.WingtipsSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsSpringBoot2WebfluxConfiguration.class)
public class ComponentTestMainManualImportOnly {

    public ComponentTestMainManualImportOnly() {
    }

}
