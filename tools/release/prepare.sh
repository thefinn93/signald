#!/bin/bash
set -euo pipefail

version="${1:-}"

if [ "${version}" == "" ]; then
    echo "Usage: ${0} [version]"
    echo ""
    echo "version should be a valid semver (semver.org)"
    exit 1
fi

prev="$(git describe --abbrev=0 HEAD)"  # get the most recent tag (most recently released version)
releasenotes_filename="releases/${version}.md"
if [ ! -f "${releasenotes_filename}" ]; then
    echo "# signald ${version}

[see all code changes here](https://gitlab.com/signald/signald/-/compare/${prev}...${version})


<!--
changes since last release:

$(git log --decorate=full --oneline ${prev}..HEAD)
-->
" > "${releasenotes_filename}"
    git add "${releasenotes_filename}"
    echo "[${releasenotes_filename}] created, please edit before releasing."
else
    echo "[${releasenotes_filename}] exists, please edit before releasing"
fi


if ! grep -q "signald (${version})" debian/changelog; then
    currentChangelog="$(cat debian/changelog)"
    debchange --newversion "${version}" -m "Full release notes at https://gitlab.com/signald/signald/-/releases/${version}"
    echo "[debian/changelog] added ${version} entry, please populate before releasing"
else
    echo "[debian/changelog] ${version} entry already exists, please edit before releasing"
fi

echo "Once the files mentioned above are updated and you are ready to release:"
echo "  ./tools/release/create.sh ${version}"