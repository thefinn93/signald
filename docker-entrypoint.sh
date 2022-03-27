#!/bin/bash
#
# Copyright 2022 signald contributors
# SPDX-License-Identifier: GPL-3.0-only
# See included LICENSE file
#
#
set -euo pipefail

[[ "${1:-}" == "break" ]] && exec /bin/bash

if [[ "${SIGNALD_DATABASE:-}" == "postgres"* ]] && [[ -f "/signald/signald.db" ]]; then
  echo "signald is configured to use a postgres database, but a sqlite file was found. running migration before starting"
  signaldctl db-move "${SIGNALD_DATABASE}" /signald/signald.db || (echo "database move failed, leaving container running for 10 minutes" && sleep 600 && exit 1)
fi

signald "$@"
