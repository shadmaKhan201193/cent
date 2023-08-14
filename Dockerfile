FROM openjdk:17-alpine
#FROM openjdk:17.0.2-jdk-oracle
#RUN apt-get update && apt-get install -y curl
COPY /build/libs/centralized-processing-fx-0.0.1.jar /app/centralized-processing-fx-0.0.1.jar
EXPOSE 8183
CMD ["java","-jar","/app/centralized-processing-fx-0.0.1.jar"]
