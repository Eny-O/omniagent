FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source=https://github.com/Eny-O/omniagent-java
LABEL org.opencontainers.image.description="OmniAgent Spring Boot backend"
WORKDIR /app
COPY --from=build /build/target/omniagent-java-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
