# syntax=docker/dockerfile:1.7-labs

##
# Build stage - compila o projeto e gera o JAR com os testes pulados (já foram rodados na CI).
##
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./

# Faz o download das dependências antes de copiar o restante para aproveitar cache.
RUN ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw -DskipTests package

##
# Runtime stage - imagem mínima apenas com o JRE e o artefato.
##
FROM eclipse-temurin:21-jre AS runtime

ENV APP_HOME=/opt/app \
	SERVER_PORT=8080 \
	JAVA_OPTS="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+AlwaysActAsServerClassMachine"

WORKDIR ${APP_HOME}

# Copia apenas o JAR final do estágio anterior.
COPY --from=build /workspace/target/java-spring-load-test-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
