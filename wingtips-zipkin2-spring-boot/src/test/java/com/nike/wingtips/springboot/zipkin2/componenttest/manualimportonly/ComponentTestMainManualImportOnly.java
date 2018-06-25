package com.nike.wingtips.springboot.zipkin2.componenttest.manualimportonly;

import com.nike.wingtips.springboot.zipkin2.WingtipsWithZipkinSpringBootConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsWithZipkinSpringBootConfiguration.class)
public class ComponentTestMainManualImportOnly {

    public ComponentTestMainManualImportOnly() {
    }

}
