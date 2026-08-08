FROM curlimages/curl:8.16.0 AS dependencies
USER root
RUN mkdir -p /artifacts \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/iceberg-spark-runtime.jar https://repo.maven.apache.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/1.11.0/iceberg-spark-runtime-3.5_2.12-1.11.0.jar \
    && curl --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 15 --proto '=https' --proto-redir '=https' --output /artifacts/iceberg-aws-bundle.jar https://repo.maven.apache.org/maven2/org/apache/iceberg/iceberg-aws-bundle/1.11.0/iceberg-aws-bundle-1.11.0.jar \
    && printf '%s  %s\n' \
        94b8e36fc329f0293d44ba9e01b784a56e9501affec1842d898144c51f6e486a /artifacts/iceberg-spark-runtime.jar \
        38f01da7e96850cdd05e6616d758b77b43314b712a8808e3f9a824d56976162f /artifacts/iceberg-aws-bundle.jar \
        | sha256sum -c -

FROM apache/spark:3.5.9-java17-python3
USER root
COPY --from=dependencies --chmod=0644 /artifacts/iceberg-spark-runtime.jar /opt/spark/jars/iceberg-spark-runtime.jar
COPY --from=dependencies --chmod=0644 /artifacts/iceberg-aws-bundle.jar /opt/spark/jars/iceberg-aws-bundle.jar
WORKDIR /workspace
USER 185
ENTRYPOINT ["/workspace/scripts/spark-run.sh"]
