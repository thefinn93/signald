#!/bin/sh
addgroup --system signald
adduser --system --home /var/lib/signald signald
adduser signald signald  # Add signald user to the signald group
chown -R signald:signald /var/lib/signald
systemctl enable signald
systemctl start signald
