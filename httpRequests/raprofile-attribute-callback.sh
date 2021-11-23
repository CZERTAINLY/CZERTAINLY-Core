#!/bin/bash

curl -X POST \
 --cacert ../ssl/server/server.crt \
 --cert ../ssl/client/client1.p12:123456 \
 --cert-type p12 \
 -H 'content-type: application/json' \
 -d '{ "callbackMethod": "listCAsInProfile", "pathVariables": { "authorityId": 1, "endEntityProfileId": 810251114 }, "queryParameters": null, "requestBody": null }' \
 https://localhost:8443/api/v1/authorities/1/callback