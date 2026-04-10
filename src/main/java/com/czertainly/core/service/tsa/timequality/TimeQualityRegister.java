package com.czertainly.core.service.tsa.timequality;

public interface TimeQualityRegister {

    TimeQualityStatus getStatus(String profile);

    void update(TimeQualityResult result);
}
