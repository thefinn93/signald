FROM gradle:6-jdk${JAVA_VERSION:-11} AS build

COPY . /tmp/src
WORKDIR /tmp/src

ARG CI_BUILD_REF_NAME
ARG CI_COMMIT_SHA

RUN VERSION=$(./version.sh) gradle -Dorg.gradle.daemon=false build
RUN tar xf build/distributions/signald.tar -C /opt

FROM gradle:6-jre${JAVA_VERSION:-11} AS release

USER root
COPY --from=build /opt/signald /opt/signald/
RUN ln -sf /opt/signald/bin/signald /usr/local/bin/
RUN  apt-get update \
  && apt-get -y install socat netcat \
  && apt-get clean \
  && rm -r /var/lib/apt/lists

COPY ./tools/signald-tcp.sh /usr/local/

VOLUME /signald
EXPOSE 15432

CMD ["/bin/bash", "/usr/local/signald-tcp.sh"]

