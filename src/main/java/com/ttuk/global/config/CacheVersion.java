package com.ttuk.global.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component
public class CacheVersion {

    private static final String LOCAL = "local";

    private final String value;

    public CacheVersion(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        if (buildProperties == null || buildProperties.getTime() == null) {
            this.value = LOCAL;
        } else {
            this.value = String.valueOf(buildProperties.getTime().getEpochSecond());
        }
    }

    public String prefix() {
        return value + ":";
    }
}