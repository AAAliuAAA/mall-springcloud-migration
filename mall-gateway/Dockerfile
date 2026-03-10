FROM eclipse-temurin:8-jre-alpine
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo 'Asia/Shanghai' > /etc/timezone
COPY target/mall-gateway-1.0-SNAPSHOT.jar /app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app.jar"]
