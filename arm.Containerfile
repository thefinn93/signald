FROM docker.io/library/golang:1.16-bullseye as signaldctl

WORKDIR /src
RUN git clone https://gitlab.com/signald/signald-go.git . && make signaldctl

FROM docker.io/library/gradle:6-jdk${JAVA_VERSION:-11} AS build

COPY . /tmp/src
WORKDIR /tmp/src

ARG CI_BUILD_REF_NAME
ARG CI_COMMIT_SHA

RUN VERSION=$(./version.sh) gradle -Dorg.gradle.daemon=false build
RUN tar xf build/distributions/signald.tar -C /opt

FROM docker.io/library/gradle:6-jre${JAVA_VERSION:-11} AS release

USER root
COPY --from=build /opt/signald /opt/signald/
RUN ln -sf /opt/signald/bin/signald /usr/local/bin/

COPY --from=signaldctl /src/signaldctl /usr/local/bin/signaldctl
RUN useradd -mu 1337 signald && mkdir /signald && chown -R signald:signald /signald

VOLUME /signald

ADD docker-entrypoint.sh /bin/entrypoint.sh

USER signald
RUN ["/usr/local/bin/signaldctl", "config", "set", "socketpath", "/signald/signald.sock"]

ENTRYPOINT ["/bin/entrypoint.sh"]
CMD ["-d", "/signald", "-s", "/signald/signald.sock"]
