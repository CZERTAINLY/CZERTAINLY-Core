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

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.http.message.StatusLine;

public class IntuneClientHttpErrorException extends IntuneClientException
{

    private static final long serialVersionUID = -2909995512671260231L;
    
    private UUID activityId = null;
    private StatusLine statusLine = null;
    //MODIFICATION - Changed the implementation to work with com.fasterxml.jackson.databind.JsonNode instead of org.json.JSONObject
    private JsonNode response = null;
    
    public int getStatusCode()
    {
        return this.statusLine.getStatusCode();
    }
    
    public JsonNode getResponse()
    {
        return this.response;
    }
    
    public UUID getActivityId()
    {
        return this.activityId;
    }
    
    public IntuneClientHttpErrorException(StatusLine statusLine, JsonNode response, UUID activityId)
    {
        //MODIFICATION - Changed the implementation to work with com.fasterxml.jackson.databind.JsonNode instead of org.json.JSONObject
        super(response.toString());
        this.activityId = activityId;
        this.statusLine = statusLine;
        this.response = response;
    }
}
