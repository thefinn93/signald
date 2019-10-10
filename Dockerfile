FROM gradle:jdk${JAVA_VERSION:-8}

COPY . /tmp/src
WORKDIR /tmp/src


RUN gradle -Dorg.gradle.daemon=false build
RUN tar xf build/distributions/signald.tar -C /opt
USER root
RUN ln -sf /opt/signald/bin/signald /usr/local/bin/

# basically `make setup`
RUN mkdir -p /var/run/signald
RUN chown gradle /var/run/signald

# Cleanup
RUN rm -rf /tmp/src

USER gradle
WORKDIR /home/gradle

ENTRYPOINT ["/usr/local/bin/signald"]
