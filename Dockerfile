FROM fabric8/java-jboss-openjdk8-jdk:1.2.1

ENV JAVA_APP_JAR hola-swarm.jar
ENV AB_OFF true
ENV JAVA_OPTIONS -Xmx512m
ENV ZIPKIN_SERVER_URL http://zipkin-query:9411
ENV KEYCLOAK_FILE /deployments/keycloak.json
ENV KEYCLOAK_SERVER_URL http://localhost:8081/auth

EXPOSE 8080

ADD keycloak.json /deployments/
RUN sed -i -e "s/KEYCLOAK_SERVER/$(echo $KEYCLOAK_SERVER_URL | sed -e 's/\\/\\\\/g; s/\//\\\//g; s/&/\\\&/g')/g" /deployments/keycloak.json
ADD target/hola-swarm.jar /deployments/
