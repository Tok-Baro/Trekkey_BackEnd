FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 10001 trekkey \
    && useradd --system --uid 10001 --gid trekkey \
        --home-dir /app --shell /usr/sbin/nologin trekkey

WORKDIR /app

COPY --chown=trekkey:trekkey build/libs/trekkey-0.0.1-SNAPSHOT.jar app.jar

USER trekkey

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
