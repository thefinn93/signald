FROM docker.io/library/golang:1.18-bullseye as signaldctl

WORKDIR /src
RUN git clone https://gitlab.com/signald/signald-go.git . && make signaldctl

FROM docker.io/library/gradle:7-jdk${JAVA_VERSION:-17} AS build

COPY . /tmp/src
WORKDIR /tmp/src

ARG CI_BUILD_REF_NAME
ARG CI_COMMIT_SHA
ARG USER_AGENT

RUN VERSION=$(./version.sh) gradle -Dorg.gradle.daemon=false runtime

FROM debian:stable-slim AS release
RUN useradd -mu 1337 signald && mkdir /signald && chown -R signald:signald /signald && \
    apt-get update && apt-get install -y locales && sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
COPY --from=build /tmp/src/build/image /
COPY --from=signaldctl /src/signaldctl /bin/signaldctl
ADD docker-entrypoint.sh /bin/entrypoint.sh
USER signald
RUN ["/bin/signaldctl", "config", "set", "socketpath", "/signald/signald.sock"]

VOLUME /signald

ENTRYPOINT ["/bin/entrypoint.sh"]
CMD ["-d", "/signald", "-s", "/signald/signald.sock"]
