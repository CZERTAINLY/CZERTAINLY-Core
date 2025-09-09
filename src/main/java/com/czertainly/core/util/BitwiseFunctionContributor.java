package com.czertainly.core.util;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;

public class BitwiseFunctionContributor implements FunctionContributor {

    public static final String BIT_AND_FUNCTION = "bitand";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        BasicType<Integer> resultType = functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.INTEGER);
        functionContributions.getFunctionRegistry().registerPattern(BIT_AND_FUNCTION,
                "?1 & ?2", resultType);
    }
}
