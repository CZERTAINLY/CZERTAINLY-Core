# Build stage
FROM maven:3.8.1-openjdk-11-slim as build
COPY src /home/app/src
COPY pom.xml /home/app
COPY settings.xml /root/.m2/settings.xml
ARG SERVER_USERNAME
ARG SERVER_PASSWORD
RUN mvn -f /home/app/pom.xml clean package
COPY docker /home/app/docker

# Package stage
#FROM openjdk:11-jdk-slim
FROM adoptopenjdk/openjdk11:alpine-jre

COPY --from=build /home/app/docker /
COPY --from=build /home/app/target/*.jar /opt/czertainly/app.jar

WORKDIR /opt/czertainly

ENTRYPOINT ["/opt/czertainly/entry.sh"]
