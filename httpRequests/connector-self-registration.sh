#!/bin/bash

curl -X POST \
 --cacert ../ssl/server/server.crt \
 -H 'content-type: application/json' \
 -d '{ "name": "selfRegConnector", "url": "localhost", "functionGroups": [] }' \
 https://localhost:8443/api/v1/connector/register
