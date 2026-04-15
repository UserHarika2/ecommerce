FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/ecommerce-app-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]