FROM openjdk:11-slim
ARG PORT
ARG VERSION
ARG WORKING_DIR
WORKDIR $WORKING_DIR
COPY ./build/libs/captain-sonar-${VERSION}-all.jar captain-sonar.jar
EXPOSE $PORT
ENTRYPOINT ["java", "-jar", "captain-sonar.jar"]
