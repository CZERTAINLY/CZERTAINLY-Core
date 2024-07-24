package com.czertainly.core.dao;

import com.czertainly.api.model.common.enums.IPlatformEnum;

public record AggregateResultDto(String aggregatedValue, Number aggregation) {

    public AggregateResultDto(Integer aggregatedValue, Number aggregation) {
        this(aggregatedValue.toString(), aggregation);
    }

    public AggregateResultDto(IPlatformEnum aggregatedValue, Number aggregation) {
        this(aggregatedValue.getCode(), aggregation);
    }
}
