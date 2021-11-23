package com.czertainly.core.aop;

import com.czertainly.core.service.AdminService;
import com.czertainly.core.service.ClientOperationService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.api.exception.ValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

@SpringBootTest
public class AuditLogAspectTest {

    @Autowired
    private AdminService adminService;
    @Autowired
    private ClientOperationService clientOperationService;
    @Autowired
    private ConnectorService connectorService;

    @Test
    @WithMockUser(roles = "SUPERADMINISTRATOR")
    public void testAuditLogAdmin() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> adminService.editAdmin(UUID.randomUUID().toString(), null));
    }

    @Test
    @WithMockUser(roles = "SUPERADMINISTRATOR")
    public void testAuditLogNestedCall() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> connectorService.createConnector(null));
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    public void testAuditLogClientOperation() {
        Assertions.assertThrows(
                ValidationException.class,
                () -> clientOperationService.addEndEntity("RAname", null));
    }
}
