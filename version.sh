#!/bin/sh
VERSION=$(git describe --exact-match 2> /dev/null) || VERSION=$(git describe --abbrev=0)+git$(date +%Y-%m-%d)r$(git rev-parse --short=8 HEAD).$(git rev-list $(git describe --abbrev=0)..HEAD --count)
echo $VERSION
