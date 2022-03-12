#!/bin/bash -ex
#
# Copyright 2022 signald contributors
# SPDX-License-Identifier: GPL-3.0-only
# See included LICENSE file
#
#

# https://github.com/signalapp/Signal-Desktop/blob/5c810c65cc78af59c77a9852d6c40fd98d122b91/aptly.sh was helpful


aptly repo create signald
aptly mirror create -ignore-signatures backfill-mirror https://updates.signald.org "${DISTRIBUTION}" main
aptly mirror update -ignore-signatures backfill-mirror

aptly repo import backfill-mirror signald signaldctl 'signald (>= 0.17.0)'

aptly repo add signald signald_*.deb

gpg1 --import "${SIGNING_KEY_PATH}"
gpg1 --list-secret-keys
aptly publish repo -config=.aptly.conf -batch -gpg-key="${SIGNING_KEY_ID}" -distribution="${DISTRIBUTION}" "signald" "s3:updates.signald.org:"
