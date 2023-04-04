FROM eclipse-temurin:17-jre

MAINTAINER CZERTAINLY <support@czertainly.com>

# add non root user czertainly
RUN addgroup --system --gid 10001 czertainly && adduser --system --home /opt/czertainly --uid 10001 --ingroup czertainly czertainly

RUN apt-get update && \
    apt-get install -y curl

COPY data/docker /
COPY data/target/*.jar /opt/czertainly/app.jar

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
