# Build stage
FROM maven:3.8.7-eclipse-temurin-17 as build

COPY src /home/app/src
COPY pom.xml /home/app
COPY settings.xml /root/.m2/settings.xml

ARG SERVER_USERNAME
ARG SERVER_PASSWORD
RUN mvn -f /home/app/pom.xml clean package
COPY docker /home/app/docker

# Package stage
FROM eclipse-temurin:17-jre-alpine

MAINTAINER CZERTAINLY <support@czertainly.com>

# add non root user czertainly
RUN addgroup --system --gid 10001 czertainly && adduser --system --home /opt/czertainly --uid 10001 --ingroup czertainly czertainly

RUN apk update && \
  apk add --no-cache curl

COPY --from=build /home/app/docker /
COPY --from=build /home/app/target/*.jar /opt/czertainly/app.jar

WORKDIR /opt/czertainly

ENV JDBC_URL=
ENV JDBC_USERNAME=
ENV JDBC_PASSWORD=
ENV DB_SCHEMA=core
ENV PORT=8080
ENV HEADER_NAME=X-APP-CERTIFICATE
ENV HEADER_ENABLED=
ENV TS_PASSWORD=
ENV OPA_BASE_URL=
ENV AUTH_SERVICE_BASE_URL=
ENV AUTH_TOKEN_HEADER_NAME=X-USERINFO
ENV AUDITLOG_ENABLED=false
ENV SCHEDULED_TASKS_ENABLED=true
ENV JAVA_OPTS=
ENV TRUSTED_CERTIFICATES=

ENV HTTP_PROXY=
ENV HTTPS_PROXY=
ENV NO_PROXY=

USER 10001

ENTRYPOINT ["/opt/czertainly/entry.sh"]
