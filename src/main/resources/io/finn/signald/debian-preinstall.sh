#!/bin/sh
addgroup --system signald
adduser --system --home /var/lib/signald signald
adduser signald signald  # Add signald user to the signald group
systemctl enable signald
systemctl start signald
