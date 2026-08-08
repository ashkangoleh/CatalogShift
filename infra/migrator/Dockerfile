FROM curlimages/curl:8.16.0 AS dependencies
USER root
RUN mkdir -p /artifacts \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/migrator.tar.gz https://github.com/apache/polaris-tools/archive/refs/tags/apache-polaris-iceberg-catalog-migrator-1.0.0-rc3.tar.gz \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/iceberg-aws-bundle.jar https://repo.maven.apache.org/maven2/org/apache/iceberg/iceberg-aws-bundle/1.10.0/iceberg-aws-bundle-1.10.0.jar \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/gradle-9.3.1-all.zip https://downloads.gradle.org/distributions/gradle-9.3.1-all.zip \
    && printf '%s  %s\n' \
        dc7b4872f3d066c60397eae21f1cfa93f4776f627b2b447711fd949cd676cee3 /artifacts/migrator.tar.gz \
        172370cfc32a5f1ae5399b210e86fc04a366dc29a112d97ac8ffc74df7525596 /artifacts/iceberg-aws-bundle.jar \
        | sha256sum -c -

FROM eclipse-temurin:21-jdk-jammy AS build
COPY --from=dependencies /artifacts/migrator.tar.gz /tmp/migrator.tar.gz
COPY --from=dependencies /artifacts/gradle-9.3.1-all.zip /tmp/gradle-9.3.1-all.zip
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/* \
    && mkdir /src && tar -xzf /tmp/migrator.tar.gz --strip-components=1 -C /src \
    && unzip -q /tmp/gradle-9.3.1-all.zip -d /opt/ \
    && cd /src/iceberg-catalog-migrator \
    && /opt/gradle-9.3.1/bin/gradle --no-daemon :iceberg-catalog-migrator-cli:shadowJar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=dependencies --chmod=0644 /artifacts/iceberg-aws-bundle.jar /app/iceberg-aws-bundle.jar
COPY --from=build /src/iceberg-catalog-migrator/cli/build/libs/iceberg-catalog-migrator-cli-1.0.0.jar /app/migrator.jar
COPY infra/migrator/entrypoint.sh /app/entrypoint.sh
WORKDIR /work
ENTRYPOINT ["/app/entrypoint.sh"]
