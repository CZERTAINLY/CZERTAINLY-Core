package com.czertainly.core.service.cmp.message.protection.impl;

import com.czertainly.core.service.cmp.message.ConfigurationContext;

public abstract class BaseProtectionStrategy {

    protected final ConfigurationContext configuration;

    protected BaseProtectionStrategy(ConfigurationContext configuration) {
        this.configuration = configuration;
    }
}
