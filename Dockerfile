# Hatua ya 1: Ku-build mradi kwa kutumia Maven na Java 17
FROM maven:3.8.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Hatua ya 2: Kukimbiza app kwa kutumia JRE nyepesi ya Java 17
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]