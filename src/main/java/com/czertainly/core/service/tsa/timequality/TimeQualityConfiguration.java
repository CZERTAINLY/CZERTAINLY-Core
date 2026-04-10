package com.czertainly.core.service.tsa.timequality;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TimeQualityConfiguration {

    @Bean
    TimeQualityRegister timeQualityRegister() {
        return new TimeQualityRegisterFake();
    }
}
