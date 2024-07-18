# Build stage
FROM maven:3.9.8-eclipse-temurin-21 as build

COPY src /home/app/src
COPY pom.xml /home/app
COPY settings.xml /root/.m2/settings.xml

ARG SERVER_USERNAME
ARG SERVER_PASSWORD

RUN apt-get update && \
    apt-get install ca-certificates curl gnupg -y && \
    mkdir -m 0755 -p /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    echo \
      "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
      tee /etc/apt/sources.list.d/docker.list > /dev/null

RUN apt-get update && \
    apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

COPY docker /home/app/docker

RUN mvn -f /home/app/pom.xml clean package

# Package stage
FROM eclipse-temurin:21.0.3_9-jre-alpine

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
ENV SCHEDULER_BASE_URL=
ENV RABBITMQ_HOST=
ENV RABBITMQ_PORT=5672
ENV RABBITMQ_USERNAME=
ENV RABBITMQ_PASSWORD=
ENV RABBITMQ_VHOST=czertainly

ENV HTTP_PROXY=
ENV HTTPS_PROXY=
ENV NO_PROXY=

USER 10001

ENTRYPOINT ["/opt/czertainly/entry.sh"]
