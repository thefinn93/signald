export VERSION = $(shell git describe --abbrev=0)+git$(shell date +%Y-%m-%d)r$(shell git rev-parse --short=8 HEAD).$(shell git rev-list $(shell git describe --abbrev=0)..HEAD --count)
export CI_PROJECT_NAME ?= signald
GRADLE=gradle

ifeq (, $(shell which $(GRADLE)))
	GRADLE="./gradlew"
endif

all: installDist tar

deb:
	gbp dch --verbose --ignore-branch --debian-tag="%(version)s" --git-author --new-version=$(VERSION)
	dpkg-buildpackage -us -uc

installDist distTar:
	$(GRADLE) $@
