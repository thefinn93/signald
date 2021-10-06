#!/bin/sh
echo $(git describe --abbrev=0 HEAD)-$(git rev-list $(git describe --abbrev=0 HEAD)..HEAD --count)-$(git rev-parse --short=8 HEAD)
