#!/bin/sh
addgroup --system signald
adduser --system --home /var/lib/signald signald
adduser signald signald  # Add signald user to the signald group
[[ -d /var/lib/signald/.config/signal ]] && mv /var/lib/signald/.config/signal/* /var/lib/signald/ && rm -rf /var/lib/signald/.config/signal
chown -R signald:signald /var/lib/signald
systemctl enable signald
systemctl start signald
