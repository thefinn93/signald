#!/bin/sh
commits="$(git rev-list $(git describe --always --abbrev=0 HEAD)..HEAD --count)"
if [ "${commits}" = "0" ]; then
    git describe --tag HEAD
else
    echo $(git describe --always --abbrev=0 HEAD)-${commits}-$(git rev-parse --short=8 HEAD)
fi