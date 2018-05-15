FROM redhat-openjdk-18/openjdk18-openshift:1.3-4

EXPOSE 8080

# Copy dependencies
COPY target/dependency/* /deployments/libs/

ENV JAVA_APP_DIR=/deployments
ENV JAVA_LIB_DIR=/deployments/libs
ENV JAVA_CLASSPATH=${JAVA_APP_DIR}/classes:${JAVA_LIB_DIR}/*
ENV JAVA_OPTIONS="-Dvertx.cacheDirBase=/tmp -Dvertx.disableDnsResolver=true -Dio.vertx.core.logging.Log4jLogDelegateFactory=io.vertx.core.logging.Log4j2LogDelegateFactory -Djava.util.logging.config.file=conf/logging.properties"
ENV JAVA_MAIN_CLASS="io.vertx.core.Launcher run me.escoffier.keynote.MainVerticle"

# Copy classes
COPY target/classes /deployments/classes

