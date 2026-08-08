FROM curlimages/curl:8.16.0 AS dependencies
USER root
RUN mkdir -p /artifacts \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/postgres.jar https://jdbc.postgresql.org/download/postgresql-42.7.7.jar \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/hadoop-aws.jar https://repo.maven.apache.org/maven2/org/apache/hadoop/hadoop-aws/3.1.0/hadoop-aws-3.1.0.jar \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/aws-java-sdk-bundle.jar https://repo.maven.apache.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.11.271/aws-java-sdk-bundle-1.11.271.jar \
    && printf '%s  %s\n' \
        157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460 /artifacts/postgres.jar \
        a18508b9348af095ea41301e439354dbd449e304ac44c6885b2b4fe78de88126 /artifacts/hadoop-aws.jar \
        faf78ac4880f56cf52791d84ec1068ce7c66acc4295d580a726104b734c01fcd /artifacts/aws-java-sdk-bundle.jar \
        | sha256sum -c -

FROM apache/hive:3.1.3
USER root
COPY --from=dependencies --chown=hive:hive --chmod=0644 /artifacts/postgres.jar /opt/hive/lib/postgres.jar
COPY --from=dependencies --chown=hive:hive --chmod=0644 /artifacts/hadoop-aws.jar /opt/hive/lib/hadoop-aws.jar
COPY --from=dependencies --chown=hive:hive --chmod=0644 /artifacts/aws-java-sdk-bundle.jar /opt/hive/lib/aws-java-sdk-bundle.jar
COPY --chown=hive:hive --chmod=0644 infra/hms/core-site.xml /opt/hadoop/etc/hadoop/core-site.xml
USER hive
