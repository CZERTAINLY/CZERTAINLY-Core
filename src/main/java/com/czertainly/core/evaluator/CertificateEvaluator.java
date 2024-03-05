package com.czertainly.core.evaluator;

import com.czertainly.core.dao.entity.Certificate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Component
public class CertificateEvaluator extends RuleEvaluator<Certificate>{

    private Map<String, BiConsumer<Certificate, Object>> fieldIdentifierToGetter;

    {
        fieldIdentifierToGetter = new HashMap<>();
        fieldIdentifierToGetter.put("commonName", ());
    }

}
