# Optimize stage
FROM eclipse-temurin:21-jdk-alpine AS optimize

COPY data/target/*.jar /app/app.jar

WORKDIR /app

# List jar modules
RUN jar xf app.jar
RUN jdeps \
  --ignore-missing-deps \
  --print-module-deps \
  --multi-release 21 \
  --recursive \
  --class-path 'BOOT-INF/lib/*' \
  app.jar > modules.txt

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
  --add-modules $(cat modules.txt),jdk.crypto.ec,jdk.jdwp.agent \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2 \
  --output /javaruntime

# Package stage
FROM alpine:latest

ENV JAVA_HOME=/opt/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# copy optimized JRE
COPY --from=optimize /javaruntime $JAVA_HOME

LABEL org.opencontainers.image.authors="CZERTAINLY <support@czertainly.com>"

# add non root user czertainly
RUN addgroup --system --gid 10001 czertainly && adduser --system --home /opt/czertainly --uid 10001 --ingroup czertainly czertainly

RUN apk update && \
  apk add --no-cache curl

COPY data/docker /
COPY data/target/*.jar /opt/czertainly/app.jar

WORKDIR /opt/czertainly

# default environment variables
ENV DB_SCHEMA=core
ENV PORT=8080
ENV HEADER_NAME=ssl-client-cert
ENV SCHEDULED_TASKS_ENABLED=true
ENV RABBITMQ_PORT=5672
ENV RABBITMQ_VHOST=czertainly

USER 10001

ENTRYPOINT ["/opt/czertainly/entry.sh"]
