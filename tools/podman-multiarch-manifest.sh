#!/usr/bin/env bash
set -exuo pipefail
#
# Copyright 2022 signald contributors
# SPDX-License-Identifier: GPL-3.0-only
# See included LICENSE file
#
#
# this script is mostly a workaround for limitations of the version of podman in debian stable. it used to be a one-liner with docker
podman manifest create $1
for i in ${@:2}; do
  podman manifest add $1 docker://$i
done
podman manifest push -f v2s2 --all $1 docker://$1