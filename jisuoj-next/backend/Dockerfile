FROM docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mkdir -p /root/.m2 \
    && printf '%s\n' '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>aliyun-public</id><mirrorOf>*</mirrorOf><name>Aliyun Maven Public</name><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml \
    && mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1536 --create-home app
WORKDIR /app
COPY --from=build /workspace/target/jisuoj-backend-*.jar app.jar
RUN mkdir -p /home/judge/data && chown -R app:app /app /home/judge
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
