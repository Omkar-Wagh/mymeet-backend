# =========================================================
# BUILD STAGE
# =========================================================

# Java 21 + Maven environment used to compile the application.
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom first so Docker can cache Maven dependencies.
COPY pom.xml .

RUN mvn dependency:go-offline -B

# Copy the application source code.
COPY src ./src

# Build the Spring Boot executable JAR.
#
# Tests are skipped here because tests should normally be
# executed separately in the CI/CD pipeline before creating
# the production image.
RUN mvn clean package -DskipTests


# =========================================================
# RUNTIME STAGE
# =========================================================

# Smaller Java 21 runtime image.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy only the generated application JAR from the build stage.
COPY --from=build /app/target/mymeet-backend-0.0.1-SNAPSHOT.jar app.jar

# Spring Boot listens on this port by default.
EXPOSE 8080

# Start the Spring Boot application.
ENTRYPOINT ["java", "-jar", "app.jar"]