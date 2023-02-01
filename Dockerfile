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

# add non root user czertainly
RUN addgroup --system --gid 10001 czertainly && adduser --system --home /opt/czertainly --uid 10001 --gid 10001 czertainly

RUN apk update && \
  apk add --no-cache curl

COPY --from=build /home/app/docker /
COPY --from=build /home/app/target/*.jar /opt/czertainly/app.jar

WORKDIR /opt/czertainly

USER czertainly

ENTRYPOINT ["/opt/czertainly/entry.sh"]
