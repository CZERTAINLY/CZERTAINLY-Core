package com.czertainly.core.util;

import com.czertainly.core.security.authz.opa.OpaClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
class SpringBootTestContext {
    @MockBean
    OpaClient opaClient;

}