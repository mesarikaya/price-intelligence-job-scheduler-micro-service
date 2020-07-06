FROM adoptopenjdk/openjdk14:ubi
LABEL PROJECT_NAME="price-intelligence-job-scheduler"
WORKDIR /opt
ENV PORT 8010
EXPOSE 8010
COPY target/*.jar /opt/app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]