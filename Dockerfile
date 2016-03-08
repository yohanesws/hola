FROM java:openjdk-8-jdk

ADD target/hola-swarm.jar /opt/wildfly-swarm.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/wildfly-swarm.jar"]