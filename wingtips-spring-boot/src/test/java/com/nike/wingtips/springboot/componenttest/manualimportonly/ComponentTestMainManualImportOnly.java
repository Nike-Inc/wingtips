package com.nike.wingtips.springboot.componenttest.manualimportonly;

import com.nike.wingtips.springboot.WingtipsSpringBootConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(WingtipsSpringBootConfiguration.class)
public class ComponentTestMainManualImportOnly {

    public ComponentTestMainManualImportOnly() {
    }

}
