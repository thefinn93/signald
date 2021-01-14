FROM gradle:jdk${JAVA_VERSION:-11} AS build

COPY . /tmp/src
WORKDIR /tmp/src

ARG CI_BUILD_REF_NAME
ARG CI_COMMIT_SHA

RUN VERSION=$(./version.sh) gradle -Dorg.gradle.daemon=false build
RUN tar xf build/distributions/signald.tar -C /opt

FROM gradle:jre${JAVA_VERSION:-11} AS release

USER root
COPY --from=build /opt/signald /opt/signald/
RUN ln -sf /opt/signald/bin/signald /usr/local/bin/

VOLUME /signald

CMD ["/usr/local/bin/signald", "-d", "/signald", "-s", "/signald/signald.sock"]
