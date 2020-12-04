#!/usr/bin/env bash
set -euo pipefail

src_file="${1:?Missing source file}"
src_version="${2:?Missing source version}"

if [ ! -f "$src_file" ]; then
	# No prebuilt to insert do nothing
	echo "No prebuilt"
	exit 0
fi

echo 'Inserting new prebuilt zkgroup'
cp -v "$src_file" build/install/signald/lib/libzkgroup.so
cd build/install/signald/lib
jar -v --update --file zkgroup-java-"$src_version".jar libzkgroup.so
rm libzkgroup.so
cd ../../../../
rm -rf zkgroup
