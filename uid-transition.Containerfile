FROM registry.gitlab.com/signald/signald:latest
USER root
ADD uid-transition-entrypoint.sh /bin/uid-transition-entrypoint.sh
ENTRYPOINT ["/bin/uid-transition-entrypoint.sh"]
CMD ["-d", "/signald", "-s", "/signald/signald.sock"]