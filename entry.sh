#!/bin/sh

./prepare-truststore.sh /opt/czertainly/trusted-certificates.pem

java -jar \
  -Dhttp.proxyHost=$HTTP_PROXY_HOST \
  -Dhttp.proxyPort=$HTTP_PROXY_PORT \
  -Dhttps.proxyHost=$HTTPS_PROXY_HOST \
  -Dhttps.proxyPort=$HTTPS_PROXY_PORT \
  -Dhttp.nonProxyHosts=$HTTP_NONPROXY_HOSTS \
  /app.jar

#exec "$@"