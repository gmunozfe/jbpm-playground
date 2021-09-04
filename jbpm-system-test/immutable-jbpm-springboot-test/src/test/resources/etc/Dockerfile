FROM openjdk:8-slim
COPY ./target/immutable-jbpm-springboot-test.jar /usr/application/ 
WORKDIR /usr/application 
ENTRYPOINT ["java", "-jar", "immutable-jbpm-springboot-test.jar"]

