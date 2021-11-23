#!/bin/sh

./prepare-truststore.sh /opt/czertainly/trusted-certificates.pem

java -jar /app.jar

#exec "$@"