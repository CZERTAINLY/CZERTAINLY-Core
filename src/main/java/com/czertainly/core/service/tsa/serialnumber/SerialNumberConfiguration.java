package com.czertainly.core.service.tsa.serialnumber;

import com.czertainly.core.service.tsa.clocksource.ClockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SerialNumberConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SerialNumberConfiguration.class);

    @Bean
    SerialNumberGenerator serialNumberGenerator(ClockSource clockSource) {
        String envValue = System.getenv("TSA_INSTANCE_ID");
        int instanceId = InstanceIdResolver.resolve();
        if (envValue != null && !envValue.isBlank()) {
            log.info("Instance ID resolved from TSA_INSTANCE_ID environment variable: {}", instanceId);
        } else {
            log.info("Instance ID resolved from IP address: {}", instanceId);
        }
        return new SnowflakeSerialNumberGenerator(clockSource, instanceId);
    }
}
