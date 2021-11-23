package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.ConnectorDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="ANONYMOUS")
public class ConnectorRegistrationServiceTest {

    @Autowired
    private ConnectorRegistrationService connectorRegistrationService;

    @Test
    public void testRegisterConnector() throws NotFoundException, AlreadyExistException {
        ConnectorDto request = new ConnectorDto();
        request.setName("testConnector");
        request.setFunctionGroups(List.of());

        connectorRegistrationService.registerConnector(request);
    }
}
