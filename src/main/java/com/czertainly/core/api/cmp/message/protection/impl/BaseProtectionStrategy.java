package com.czertainly.core.api.cmp.message.protection.impl;

import com.czertainly.core.api.cmp.message.ConfigurationContext;

public abstract class BaseProtectionStrategy {

    protected final ConfigurationContext configuration;

    protected BaseProtectionStrategy(ConfigurationContext configuration) {
        this.configuration = configuration;
    }
}
