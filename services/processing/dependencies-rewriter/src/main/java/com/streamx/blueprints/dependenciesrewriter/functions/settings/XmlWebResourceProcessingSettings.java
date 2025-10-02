package com.streamx.blueprints.dependenciesrewriter.functions.settings;

import com.streamx.blueprints.dependenciesrewriter.functions.settings.contentprocessing.XmlContentProcessingSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@Priority(2)
@ApplicationScoped
public class XmlWebResourceProcessingSettings extends BaseWebResourceProcessingSettings {

  @PostConstruct
  void init() {
    loadSettings(
        new XmlContentProcessingSettings(configuration),
        ".xml"
    );
  }
}