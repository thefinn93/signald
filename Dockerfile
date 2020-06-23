FROM gradle:jdk${JAVA_VERSION:-8} AS build

COPY . /tmp/src
WORKDIR /tmp/src


RUN gradle -Dorg.gradle.daemon=false build
RUN tar xf build/distributions/signald.tar -C /opt

FROM gradle:jre${JAVA_VERSION:-8}-alpine AS release

USER root
WORKDIR /opt
COPY --from=build /opt/signald /opt/signald/
RUN ln -sf /opt/signald/bin/signald /usr/local/bin/

# basically `make setup`
RUN mkdir -p /var/run/signald
RUN chown gradle /var/run/signald

USER gradle
WORKDIR /home/gradle

ENTRYPOINT ["/usr/local/bin/signald"]
