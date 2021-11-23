#!/bin/bash

curl -v -X PUT \
 --cacert ../ssl/server/server.crt \
 --cert ../ssl/client/client1.p12:123456 \
 --cert-type p12 \
 -H 'content-type: application/json' \
 -d '{ "email": "sadas@seas.cz", "name": "admin-test" }' \
 https://localhost:8443/api/v1/auth/profile
