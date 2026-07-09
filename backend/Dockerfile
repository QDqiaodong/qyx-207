FROM ${DOCKER_REGISTRY:-}maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -DskipTests -q

COPY src ./src
RUN mvn package -DskipTests -q

FROM ${DOCKER_REGISTRY:-}amazoncorretto:17-alpine-jdk

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8137

ENTRYPOINT ["java", "-jar", "app.jar"]