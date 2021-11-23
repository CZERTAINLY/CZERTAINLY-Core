#!/bin/bash

curl -X PUT \
 --cacert ../ssl/server/server.crt \
 --cert ../ssl/client/client1.p12:123456 \
 --cert-type p12 \
 -H 'content-type: application/json' \
 -d '{ "url": "http://localhost:8081"}' \
 https://localhost:8443/api/v1/connectors/connect
