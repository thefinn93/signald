.PHONY: all deb installDist distTar clean

export VERSION ?= $(shell ./version.sh)
export CI_PROJECT_NAME ?= signald
GRADLE=gradle

ifeq (, $(shell which $(GRADLE)))
	GRADLE="./gradlew"
endif

all: installDist tar

deb:
	gbp dch --verbose --ignore-branch --debian-tag="%(version)s" --git-author --new-version=$(VERSION)
	dpkg-buildpackage -us -uc -b

installDist distTar:
	$(GRADLE) $@

setup:
	sudo mkdir -p /var/run/signald
	sudo chown $(shell whoami) /var/run/signald
