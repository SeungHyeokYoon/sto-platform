# 백엔드 이미지. jar는 호스트에서 빌드한다(컨트랙트 래퍼가 ../contracts/src를 참조하므로).
#   ./gradlew bootJar  →  build/libs/platform-0.0.1-SNAPSHOT.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
