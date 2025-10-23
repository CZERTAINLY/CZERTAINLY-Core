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
package com.czertainly.core.intune.scepvalidation;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.czertainly.core.intune.carequest.CARevocationRequest;
import com.czertainly.core.intune.carequest.CARevocationResult;

/**
 * Client to access the retrieve CA Revocation Requests from Intune
 */
public class IntuneRevocationClient extends IntuneClient
{

    private ObjectMapper objectMapper = new ObjectMapper();
    private String serviceVersion = "5019-05-05";
    public final static String CONNECTOR_SERVICE_NAME = "PkiConnectorFEService";
    public final static String DOWNLOADREVOCATIONREQUESTS_URL = "CertificateAuthorityRequests/downloadRevocationRequests";
    public final static String UPLOADREVOCATIONRESULTS_URL = "CertificateAuthorityRequests/uploadRevocationResults";
    public final int MAXREQUESTS_MAXVALUE = 500;
    
    private final static String SERVICE_VERSION_PROP_NAME = CONNECTOR_SERVICE_NAME + "Version";
    private final static String PROVIDER_NAME_AND_VERSION_NAME = "PROVIDER_NAME_AND_VERSION";
    
    private HashMap<String,String> additionalHeaders = new HashMap<String, String>();;
    
    private static final Logger log = LoggerFactory.getLogger(IntuneRevocationClient.class);
    
    /**
     * IntuneScepService Client constructor
     * @param configProperties Properties object containing client configuration information.
     * @throws IllegalArgumentException
     */
    public IntuneRevocationClient(Properties configProperties) throws IllegalArgumentException 
    {
        this(configProperties, null, null, null);
    }
    
    /**
     * IntuneScepService Client constructor meant for dependency injection
     * @param configProperties
     * @param adalClient
     * @param httpClientBuilder
     * @throws IllegalArgumentException
     */
    public IntuneRevocationClient(Properties configProperties, MSALClientWrapper msalClient, ADALClientWrapper adalClient, HttpClientBuilder httpClientBuilder) throws IllegalArgumentException
    {
        super(configProperties, msalClient, adalClient, httpClientBuilder);
        
        if(configProperties == null)
        {
            throw new IllegalArgumentException("The argument 'configProperties' is missing"); 
        }
        
        configProperties.getProperty(SERVICE_VERSION_PROP_NAME,this.serviceVersion);
        
        String providerNameAndVersion = configProperties.getProperty(PROVIDER_NAME_AND_VERSION_NAME);
        if(providerNameAndVersion == null)
        {
            throw new IllegalArgumentException("The property '" + PROVIDER_NAME_AND_VERSION_NAME + "' is missing from the property file.");
        }
        
        additionalHeaders.put("UserAgent", providerNameAndVersion);
    }

    /**
     * Downloads a list of Revocation Requests from Intune 
     * 
     * @param transactionId The transactionId 
     * @param maxCARequestsToDownload Maximum number of requests to download from Intune
     * @param issuerName Optional filter for the issuer name
     * @throws IntuneClientException The service reported a failure in processing the notification examine the exception error code.
     * @throws IllegalArgumentException
     */
    public List<CARevocationRequest> DownloadCARevocationRequests(String transactionId, int maxCARequestsToDownload, String issuerName) throws IntuneScepServiceException, Exception
    {
    	// Validate Parameters
        if(transactionId == null || transactionId.isEmpty())
        {
            throw new IllegalArgumentException("The argument 'transactionId' is missing");
        }
        if (maxCARequestsToDownload <= 0 || maxCARequestsToDownload > MAXREQUESTS_MAXVALUE)
        {
            throw new IllegalArgumentException("The argument 'maxCARequestsToDownload' should be between 1 and " + MAXREQUESTS_MAXVALUE + ". Value received: " + maxCARequestsToDownload);
        }

        //MODIFICATION - Changed the implementation to work with com.fasterxml.jackson.databind.JsonNode instead of org.json.JSONObject
        ObjectNode requestBody = objectMapper.createObjectNode();
        // Create Request Body 
        requestBody.put(
                "downloadParameters", (objectMapper.createObjectNode())
                    .put("maxRequests", maxCARequestsToDownload)
                    .put("issuerName", issuerName == null ? null : issuerName));
        UUID activityId = UUID.randomUUID();
        
        // Send the POST request to Intune
        JsonNode result = this.PostRequest(CONNECTOR_SERVICE_NAME,
        		 DOWNLOADREVOCATIONREQUESTS_URL, 
                 serviceVersion, 
                 requestBody,
                 activityId,
                 additionalHeaders);
        log.info("Activity " + activityId + " has completed.");
        log.info(result.toString());
        
        // Parse the results and return
        List<CARevocationRequest> revokeRequests = new Gson().fromJson(result.get("value").toString(), new TypeToken<List<CARevocationRequest>>() {}.getType());
        return revokeRequests;
    }

    /**
     * Uploads a list of Revocation Results to Intune 
     * 
     * @param transactionId The transactionId 
     * @param revocationResults List of CARevocationResult objects to send to Intune
     * @throws IllegalArgumentException
     * @throws IntuneClientException The service reported a failure in processing the notification examine the exception error code.
     */
    public void UploadRevocationResults(String transactionId, List<CARevocationResult> revocationResults) throws IntuneScepServiceException, Exception
    {
    	// Validate Parameters
        if(transactionId == null || transactionId.isEmpty())
        {
            throw new IllegalArgumentException("The argument 'transactionId' is missing");
        }
        if (revocationResults == null || revocationResults.isEmpty())
        {
            throw new IllegalArgumentException("The argument 'revocationResults' may not be null or empty.");
        }
        
        // Create Request Body
        //MODIFICATION - Changed the implementation to work with com.fasterxml.jackson.databind.JsonNode instead of org.json.JSONObject
        ObjectNode requestBody = objectMapper.createObjectNode();
        String revocationResultsJson =  new Gson().toJsonTree(revocationResults).getAsJsonArray().toString();
        requestBody.put(
                "results", objectMapper.readTree(revocationResultsJson));
        UUID activityId = UUID.randomUUID();
        
        // Send the POST request to Intune
        JsonNode result = this.PostRequest(CONNECTOR_SERVICE_NAME,
        		 UPLOADREVOCATIONRESULTS_URL, 
                 serviceVersion,
                 requestBody,
                 activityId,
                 additionalHeaders);
        log.info("Activity " + activityId + " has completed.");
        log.info(result.toString());
        
        // Parse the result and fail if the result is not true
        if (!result.get("value").asBoolean())
        {
        	throw new IntuneClientException("Intune failed to process the upload results.");
        }
    }    
}