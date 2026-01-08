package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.common.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v2.GroupAttributeV2;
import com.czertainly.api.model.common.attribute.v3.GroupAttributeV3;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;

class CallbackServiceTest extends BaseSpringBootTest {

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private CallbackService callbackService;

    @Test
    void testCallback() throws ConnectorException, NotFoundException {
        WireMockServer mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());
        GroupAttributeV2 groupAttributeV2 = new GroupAttributeV2();
        groupAttributeV2.setName("name");
        AttributeCallback callback = new AttributeCallback();
        callback.setCallbackContext("/callback");
        callback.setCallbackMethod("GET");
        groupAttributeV2.setAttributeCallback(callback);

        GroupAttributeV3 groupAttributeV3 = new GroupAttributeV3();
        groupAttributeV3.setName("name");
        groupAttributeV3.setAttributeCallback(callback);
        DataAttributeV2 extraAttribute = new DataAttributeV2();
        extraAttribute.setName("extra");
        extraAttribute.setAttributeCallback(callback);
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/[^/]+/[^/]+/attributes")).willReturn(WireMock.okJson(AttributeDefinitionUtils.serialize(List.of(groupAttributeV2, extraAttribute, groupAttributeV3)))));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/callback")).willReturn(WireMock.okJson("{\"property\": \"value\"}")));


        Connector connector = new Connector();
        connector.setUrl(mockServer.baseUrl());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connectorRepository.save(connector);

        RequestAttributeCallback requestAttributeCallback = new RequestAttributeCallback();
        requestAttributeCallback.setName(groupAttributeV2.getName());
        Object callbackObject = callbackService.callback(connector.getUuid().toString(), FunctionGroupCode.AUTHORITY_PROVIDER, "kind", requestAttributeCallback);
        Assertions.assertEquals("value", ((LinkedHashMap<String, String>) callbackObject).get("property"));

        requestAttributeCallback.setName(extraAttribute.getName());
        callbackObject = callbackService.callback(connector.getUuid().toString(), FunctionGroupCode.AUTHORITY_PROVIDER, "kind", requestAttributeCallback);
        Assertions.assertEquals("value", ((LinkedHashMap<String, String>) callbackObject).get("property"));

        requestAttributeCallback.setName(groupAttributeV3.getName());
        callbackObject = callbackService.callback(connector.getUuid().toString(), FunctionGroupCode.AUTHORITY_PROVIDER, "kind", requestAttributeCallback);
        Assertions.assertEquals("value", ((LinkedHashMap<String, String>) callbackObject).get("property"));


    }
}
