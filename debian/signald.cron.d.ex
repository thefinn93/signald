#
# Regular cron jobs for the signald package
#
0 4	* * *	root	[ -x /usr/bin/signald_maintenance ] && /usr/bin/signald_maintenance
