package com.otilm.core.util;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class TestCostTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void prepareTestInstance(TestContext testContext) {
        TestCostRecorder.recordContext(testContext.getApplicationContext());
    }
}
