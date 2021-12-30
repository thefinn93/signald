#!/bin/bash
set -euo pipefail

# this script creates a new release.

version="$1"

if [ "${version}" == "" ]; then
    echo "Usage: ${0} [version]"
    echo ""
    echo "version should be a valid semver (semver.org)"
    exit 1
fi

if [ ! -f "./releases/${version}.md" ]; then
    echo "Please prepare the release first:"
    echo "  ./tools/release/prepare.sh ${version}"
    exit 1
fi

set -x

# update debian changelog to mark $VERSION released
dch -r -m --distribution unstable ignored

# make a commit with the released changelog
git commit -am "Release signald ${version}"

# tag the commit
git tag --annotate --message "${version}" "${version}"

echo ""
echo "Upload the release to the server with:"
echo "  git push && git push --tags"