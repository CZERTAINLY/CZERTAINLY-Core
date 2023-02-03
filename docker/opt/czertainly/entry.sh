#!/bin/sh

czertainlyHome="/opt/czertainly"
source ${czertainlyHome}/static-functions

#if [ -f ${czertainlyHome}/trusted-certificates.pem ]
#then
#  log "INFO" "Adding additional trusted certificates to cacerts"
#  ./update-cacerts.sh /opt/czertainly/trusted-certificates.pem
#
#  log "INFO" "Preparing truststore for the Core"
#  ./prepare-truststore.sh /opt/czertainly/trusted-certificates.pem
#
#  log "INFO" "Launching the Core"
#  java $JAVA_OPTS -jar ./app.jar
#else
#  log "ERROR" "No trusted certificates were provided!"
#fi

log "INFO" "Launching the Core"
java $JAVA_OPTS -jar ./app.jar

#exec "$@"