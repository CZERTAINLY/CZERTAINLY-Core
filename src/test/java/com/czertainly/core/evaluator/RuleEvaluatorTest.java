package com.czertainly.core.evaluator;

import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.RuleCondition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

@SpringBootTest
public class RuleEvaluatorTest {

    @Autowired
    private RuleEvaluator<Certificate> certificateRuleEvaluator;
    @Autowired
    private RuleEvaluator<CryptographicKey> cryptographicKeyRuleEvaluator;

    @Test
    public void testCertificateEvaluatorOnProperties() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, ParseException {

        Certificate certificate = new Certificate();
        certificate.setCommonName("Common Name");
        RuleCondition condition = new RuleCondition();
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        condition.setFieldIdentifier("commonName");
        condition.setOperator(FilterConditionOperator.NOT_EMPTY);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        condition.setValue("Common Name");
        condition.setOperator(FilterConditionOperator.EQUALS);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        condition.setOperator(FilterConditionOperator.NOT_EQUALS);
        certificate.setCommonName("Common NameE");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        condition.setOperator(FilterConditionOperator.CONTAINS);
        condition.setValue("Name");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        condition.setOperator(FilterConditionOperator.NOT_CONTAINS);
        condition.setValue("abc");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        condition.setOperator(FilterConditionOperator.STARTS_WITH);
        condition.setValue("Comm");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        condition.setOperator(FilterConditionOperator.ENDS_WITH);
        condition.setValue("eE");
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        certificate.setCommonName(null);
        condition.setOperator(FilterConditionOperator.EMPTY);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));

        certificate.setKeySize(30);
        condition.setFieldIdentifier("keySize");
        condition.setOperator(FilterConditionOperator.GREATER);
        condition.setValue(15);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));

        certificate.setNotBefore(new SimpleDateFormat(("dd.MM.yyyy")).parse("01.01.2000"));
        condition.setFieldIdentifier("notBefore");
        condition.setValue(new SimpleDateFormat(("dd.MM.yyyy")).parse("01.01.1999"));
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));

        certificate.setTrustedCa(true);
        condition.setFieldIdentifier("trustedCa");
        condition.setOperator(FilterConditionOperator.EQUALS);
        condition.setValue(true);
        Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        for (int i = 0; i < 100000; i++) {
            Assertions.assertTrue(certificateRuleEvaluator.evaluateCondition(condition, certificate));
        }
    }

    @Test
    public void testsCryptographicKeyRuleEvaluator() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CryptographicKey cryptographicKey = new CryptographicKey();
        cryptographicKey.setName("Key");
        RuleCondition condition = new RuleCondition();
        condition.setFieldSource(FilterFieldSource.PROPERTY);
        condition.setFieldIdentifier("name");
        condition.setOperator(FilterConditionOperator.NOT_EMPTY);
        Assertions.assertTrue(cryptographicKeyRuleEvaluator.evaluateCondition(condition, cryptographicKey));
    }
}
