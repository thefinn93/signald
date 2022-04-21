#!/usr/bin/env bash
#
# Copyright 2022 signald contributors
# SPDX-License-Identifier: GPL-3.0-only
# See included LICENSE file
#
#
set -euo pipefail
if [[ "$(id -u)" == "0" ]]; then
  currentFlag=""
  for arg in $@; do
    if [[ "${currentFlag}" == "" ]]; then
      currentFlag="${arg}"
    else
      if [[ "${currentFlag}" == "-d" ]]; then
        echo "Updating permissions on ${arg} (for info see \"migrating from versions before 0.18.0\" on https://signald.org/articles/install/docker/)"
        chown -R signald:signald "${arg}"
      fi
      currentFlag=""
    fi
  done
  su -c "/bin/entrypoint.sh $*" signald
else
  echo "not chowning signald data directory, if you have permission issues see https://signald.org/articles/install/docker/#migrating-from-versions-before-0180"
  /bin/entrypoint.sh $*
fi
