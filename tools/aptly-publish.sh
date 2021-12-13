#!/bin/bash -ex
#
# Copyright (C) 2021 Finn Herzfeld
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

# https://github.com/signalapp/Signal-Desktop/blob/5c810c65cc78af59c77a9852d6c40fd98d122b91/aptly.sh was helpful


aptly repo create signald
aptly mirror create -ignore-signatures backfill-mirror https://updates.signald.org "${DISTRIBUTION}" main
aptly mirror update -ignore-signatures backfill-mirror

for version in $(aptly package show signald | grep Version | awk '{print $2}' | grep -v "git"); do
  aptly repo import backfill-mirror signald "signald (= $version)"
done

aptly repo import backfill-mirror signald signaldctl 'signald (>= 0.14.0)'

aptly repo add signald signald_*.deb

gpg1 --import "${SIGNING_KEY_PATH}"
gpg1 --list-secret-keys
aptly publish repo -config=.aptly.conf -batch -gpg-key="${SIGNING_KEY_ID}" -distribution="${DISTRIBUTION}" "signald" "s3:updates.signald.org:"