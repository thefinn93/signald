socat -d TCP4-LISTEN:15432,fork UNIX-CONNECT:/tmp/signald.sock &

JAVA_OPTS="-Xms256m -Xmx1024m"  /usr/local/bin/signald -d /signald -s /tmp/signald.sock -v