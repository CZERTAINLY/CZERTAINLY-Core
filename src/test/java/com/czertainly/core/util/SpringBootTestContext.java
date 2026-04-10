package com.czertainly.core.util;

import com.czertainly.core.messaging.producers.AuditLogsProducer;
import com.czertainly.core.security.authz.opa.OpaClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestConfiguration
class SpringBootTestContext {
    @MockBean
    OpaClient opaClient;

    @MockitoBean
    AuditLogsProducer auditLogsProducer;
}
