#!/bin/bash

curl -v -X GET \
 --cacert ../ssl/server/server.crt \
 --cert ../ssl/client/client1.p12:123456 \
 --cert-type p12 \
 --output file \
 "https://localhost:8443/api/v1/logs/export?operationStatus=SUCCESS&origination=BE&sort=created,desc"
