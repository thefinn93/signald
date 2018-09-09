#!/bin/sh
addgroup --system signald
adduser --system --no-create-home signald
adduser signald signald  # Add signald user to the signald group
