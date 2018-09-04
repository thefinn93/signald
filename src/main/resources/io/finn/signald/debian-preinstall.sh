#!/bin/sh
addgroup signald
adduser --system --no-create-home signald
adduser signald signald  # Add signald user to the signald group
mkdir -p /var/run/signald
chown -R :signald /var/run/signald
