.PHONY: all deb installDist distTar clean format

export VERSION ?= $(shell ./version.sh)
export CI_PROJECT_NAME ?= signald
export CI_BUILD_REF_NAME ?= $(shell git rev-parse --abbrev-ref HEAD)
export CI_COMMIT_SHA ?= $(shell git rev-parse HEAD)
GRADLE ?= ./gradlew

all: installDist

deb:
	gbp dch --verbose --ignore-branch --debian-tag="%(version)s" --git-author --new-version=$(VERSION)
	dpkg-buildpackage -us -uc -b

installDist distTar:
	$(GRADLE) $@

integrationTest:
	export SIGNAL_URL=https://signal-server.signald.org
	$(GRADLE) integrationTest --info

format:
	find src/ -name '*.java' -exec clang-format-11 -i {} \;

setup:
	sudo mkdir -p /var/run/signald
	sudo chown $(shell whoami) /var/run/signald

src/integration-tests/resources/io/finn/signald/whisper.store: tools/letsencrypt-x1.pem
	keytool -import -file tools/letsencrypt-x1.pem -keystore src/integration-tests/resources/io/finn/signald/whisper.store -storepass whisper -storetype BKS -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath build/install/signald/lib/bcprov-jdk15on-1.66.jar

validate-protocol: installDist
	build/install/signald/bin/signald --dump-protocol | go run ./tools/protocol-validator