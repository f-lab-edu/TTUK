package com.ttuk.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "ttuk.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
