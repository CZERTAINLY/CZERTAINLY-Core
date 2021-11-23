#!/bin/bash

CERT=$(openssl pkcs12 -nokeys -clcerts -in ../ssl/client/client2.p12 -passin pass:123456)

NORM_CERT=$(echo "$CERT" | tail -n +6)
NORM_CERT=$(echo "$NORM_CERT" | sed -e 's/-----BEGIN CERTIFICATE-----//g' | sed -e 's/-----END CERTIFICATE-----//g' | sed -e 's/ //g' | sed ':a;N;$!ba;s/\n//g')

curl -v -X GET \
 -H "X-APP-CERTIFICATE: $NORM_CERT" \
 --cacert ../ssl/server/server.crt \
 --cert ../ssl/client/client1.p12:123456 \
 --cert-type p12 \
 https://localhost:8443/api/v1/raprofiles
