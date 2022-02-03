FROM golang:1.16-bullseye as signaldctl

WORKDIR /src
RUN git clone https://gitlab.com/signald/signald-go.git . \
    && make signaldctl

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

COPY --from=signaldctl /src/signaldctl /opt/signaldctl
RUN ln -sf /opt/signaldctl /usr/local/bin/
RUN mkdir -p /root/.config/ && echo "socketpath: /signald/signald.sock" > /root/.config/signaldctl.yaml

VOLUME /signald

CMD ["/usr/local/bin/signald", "-d", "/signald", "-s", "/signald/signald.sock"]
