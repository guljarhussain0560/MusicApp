FROM openjdk:21-jdk-slim

WORKDIR /app
ENV MY_FRONTEND_URL=http://localhost:5173

COPY target/musicapp.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]