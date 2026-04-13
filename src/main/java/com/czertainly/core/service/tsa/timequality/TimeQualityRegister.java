package com.czertainly.core.service.tsa.timequality;

import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;

public interface TimeQualityRegister {

    TimeQualityStatus getStatus(TimeQualityConfigurationModel profile);

    void update(TimeQualityResult result);
}
