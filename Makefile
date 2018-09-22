.PHONY: all installDist distTar deb clean setup

export VERSION = $(shell git describe --always --tags)
export CI_PROJECT_NAME ?= signald
GRADLE=gradle

ifeq (, $(shell which $(GRADLE)))
	GRADLE="./gradlew"
endif

all: installDist tar deb

installDist distTar deb:
	$(GRADLE) $@

clean:
	rm -rf build/ .gradle

setup:
	sudo mkdir -p /var/run/signald
	sudo chown $(shell whoami) /var/run/signald
