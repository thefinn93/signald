export VERSION = $(shell git describe --always --tags)
export CI_PROJECT_NAME ?= signald
GRADLE=gradle

ifeq (, $(shell which $(GRADLE)))
	GRADLE="./gradlew"
endif

all: installDist tar deb

installDist distTar deb:
	$(GRADLE) $@
