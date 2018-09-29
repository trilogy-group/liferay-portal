FROM ubuntu

RUN \
  apt-get update && \
  apt-get install -y git ant openjdk-8-jdk-headless && \
  apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN update-java-alternatives -s java-1.8.0-openjdk-amd64

ENV ANT_OPTS="-Xmx5125m"
ENV JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk-amd64"

EXPOSE 8080/tcp
  
