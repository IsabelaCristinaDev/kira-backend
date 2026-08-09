FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S kira && adduser -S kira -G kira
COPY --from=build /app/target/*.jar app.jar
RUN chown kira:kira app.jar
USER kira
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]