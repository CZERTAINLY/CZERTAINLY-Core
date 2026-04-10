package com.czertainly.core.service.tsa.timequality;


import org.springframework.stereotype.Component;

@Component
public class TimeQualityRegisterFake implements TimeQualityRegister {

    public void update(TimeQualityResult result) {

    }

    public TimeQualityStatus getStatus(String profile) {
        return TimeQualityStatus.OK;
    }
}
