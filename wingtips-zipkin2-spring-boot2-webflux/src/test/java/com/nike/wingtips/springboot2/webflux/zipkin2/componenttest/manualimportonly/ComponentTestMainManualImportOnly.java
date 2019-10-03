package com.nike.wingtips.springboot2.webflux.zipkin2.componenttest.manualimportonly;

import com.nike.wingtips.springboot2.webflux.zipkin2.WingtipsWithZipkinSpringBoot2WebfluxConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBoot2WebfluxConfiguration.class)
public class ComponentTestMainManualImportOnly {

    public ComponentTestMainManualImportOnly() {
    }

}
