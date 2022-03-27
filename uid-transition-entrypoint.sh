#!/usr/bin/env bash
#
# Copyright 2022 signald contributors
# SPDX-License-Identifier: GPL-3.0-only
# See included LICENSE file
#
#
set -euo pipefail

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