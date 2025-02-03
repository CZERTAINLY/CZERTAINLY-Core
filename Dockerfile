FROM eclipse-temurin:21.0.6_7-jre-alpine

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
