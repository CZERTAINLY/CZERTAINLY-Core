package com.czertainly.core.aop;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.service.ClientOperationService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AuditLogAspectTest extends BaseSpringBootTest {

    @Autowired
    private ClientOperationService clientOperationService;
    @Autowired
    private ConnectorService connectorService;


    @Test
    public void testAuditLogNestedCall() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> connectorService.createConnector(null));
    }

    @Test
    public void testAuditLogClientOperation() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> clientOperationService.addEndEntity("RAname", null));
    }
}
