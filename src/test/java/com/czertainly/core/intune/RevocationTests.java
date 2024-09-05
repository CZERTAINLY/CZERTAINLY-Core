// Copyright (c) Microsoft Corporation.
// All rights reserved.
//
// This code is licensed under the MIT License.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

/*
This class was updated for the integration of the platform with the Intune server.
It is placed under the package `com.czertainly.core.intune` and further maintained by
the development team.

The important modification are marked with the comment "MODIFICATION"
*/
package com.czertainly.core.intune;

import static org.junit.jupiter.api.Assertions.*;

import com.czertainly.core.intune.scepvalidation.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import com.czertainly.core.intune.carequest.CARequestErrorCodes;
import com.czertainly.core.intune.carequest.CARevocationRequest;
import com.czertainly.core.intune.carequest.CARevocationResult;

public class RevocationTests {
    @Test
    public void DownloadCARevocationRequests_Success() throws Exception {
        Helper helper = new Helper();

        // Create a list of CARevocationRequests to return
        List<CARevocationRequest> list = new ArrayList<CARevocationRequest>();
        list.add(new CARevocationRequest("requestContext1", "serialNumber1", "issuerName1", "caConfig1"));
        list.add(new CARevocationRequest("requestContext2", "serialNumber2", "issuerName2", "caConfig2"));
        JsonArray jsonArray = new Gson().toJsonTree(list).getAsJsonArray();
        //MODIFICATION - Changed the implementation to work with com.fasterxml.jackson.databind.JsonNode instead of org.json.JSONObject
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonResponse = objectMapper.createObjectNode();
        jsonResponse.put("@odata.context", "https://manage.microsoft.com/RACerts/StatelessPkiConnectorService/$metadata#Collection(microsoft.management.services.api.caRevocationRequest)")
                .put("value", objectMapper.readTree(jsonArray.toString()));
        String validJsonResponse = jsonResponse.toString();

        // Mock-out Intune Return Value
        when(helper.intuneResponseEntity.getContent())
                .thenReturn(new ByteArrayInputStream(validJsonResponse.getBytes()));
        when(helper.intuneResponseEntity.getContentLength())
                .thenReturn((long) validJsonResponse.length());

        IntuneRevocationClient client = new IntuneRevocationClient(helper.properties, helper.msal, helper.adal, helper.httpBuilder);

        UUID transactionId = UUID.randomUUID();

        List<CARevocationRequest> results = client.DownloadCARevocationRequests(transactionId.toString(), 10, null);

        verify(helper.msal, times(2)).getAccessToken(ArgumentMatchers.anySet());
        verify(helper.adal, times(0)).getAccessTokenFromCredential(anyString());

        verify(helper.httpClient, times(1)).execute(
                argThat(new ArgumentMatcher<HttpUriRequest>() {
                    @Override
                    public boolean matches(HttpUriRequest resp) {
                        return resp.getURI().getHost().equals(Helper.MSAL_URL);
                    }
                }));

        verify(helper.httpClient, times(1)).execute(
                argThat(new ArgumentMatcher<HttpUriRequest>() {
                    @Override
                    public boolean matches(HttpUriRequest resp) {
                        return resp.getURI().getHost().equals(Helper.SERVICE_URL);
                    }
                }));

        assertNotNull(results);
        assertEquals(2, results.size());
        assertNotNull(results.get(0));
        assertEquals("requestContext1", results.get(0).requestContext);
        assertEquals("serialNumber1", results.get(0).serialNumber);
        assertEquals("issuerName1", results.get(0).issuerName);
        assertEquals("caConfig1", results.get(0).caConfiguration);
        assertNotNull(results.get(1));
        assertEquals("requestContext2", results.get(1).requestContext);
        assertEquals("serialNumber2", results.get(1).serialNumber);
        assertEquals("issuerName2", results.get(1).issuerName);
        assertEquals("caConfig2", results.get(1).caConfiguration);
    }

    @org.junit.jupiter.api.Test
    public void UploadRevocationResults_Success() throws Exception {
        Helper helper = new Helper();

        // Create a list of CARevocationRequests to upload
        List<CARevocationResult> list = new ArrayList<CARevocationResult>();
        list.add(new CARevocationResult("requestContext1", true, CARequestErrorCodes.None, null));
        list.add(new CARevocationResult("requestContext1", false, CARequestErrorCodes.AuthenticationException, "Error Test"));

        // Mock-out Intune Return Value
        String response = "{\"value\":true}";
        when(helper.intuneResponseEntity.getContent())
                .thenReturn(new ByteArrayInputStream(response.getBytes()));
        when(helper.intuneResponseEntity.getContentLength())
                .thenReturn((long) response.length());

        IntuneRevocationClient client = new IntuneRevocationClient(helper.properties, helper.msal, helper.adal, helper.httpBuilder);

        UUID transactionId = UUID.randomUUID();

        client.UploadRevocationResults(transactionId.toString(), list);

        verify(helper.msal, times(2)).getAccessToken(ArgumentMatchers.anySet());
        verify(helper.adal, times(0)).getAccessTokenFromCredential(anyString());

        verify(helper.httpClient, times(1)).execute(
                argThat(new ArgumentMatcher<HttpUriRequest>() {
                    @Override
                    public boolean matches(HttpUriRequest resp) {
                        return resp.getURI().getHost().equals(Helper.MSAL_URL);
                    }
                }));

        verify(helper.httpClient, times(1)).execute(
                argThat(new ArgumentMatcher<HttpUriRequest>() {
                    @Override
                    public boolean matches(HttpUriRequest resp) {
                        return resp.getURI().getHost().equals(Helper.SERVICE_URL);
                    }
                }));
    }
}