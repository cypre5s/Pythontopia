package com.pythontopia.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "pythontopia.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class SchedulingConfig {}
