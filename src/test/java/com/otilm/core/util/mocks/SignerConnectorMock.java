package com.otilm.core.util.mocks;

import com.otilm.api.model.client.connector.v2.ConnectorInterface;

import java.util.List;

/**
 * Mock of a V2 signature-formatting connector — stubs {@code GET /v2/info} advertising
 * {@link ConnectorInterface#SIGNATURE_FORMATTING}.
 */
public class SignerConnectorMock extends BaseConnectorMock {

    SignerConnectorMock() {
        stubV2Info(List
                .of(ConnectorInterface.INFO, ConnectorInterface.HEALTH, ConnectorInterface.METRICS,
                        ConnectorInterface.SIGNATURE_FORMATTING));
    }
}
