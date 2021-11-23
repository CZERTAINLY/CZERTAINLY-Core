#!/bin/bash

curl -v -X POST \
 --cacert ../ssl/server/server.crt \
 --cert ../ssl/client/client1.p12:123456 \
 --cert-type p12 \
 -H 'content-type: application/json' \
 -d '[]' \
 https://localhost:8443/api/v1/connectors/2/attributes/validate
