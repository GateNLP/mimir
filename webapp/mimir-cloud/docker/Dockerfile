FROM tomcat:8-jre8

WORKDIR /data

RUN set -x \
        && rm -rf $CATALINA_HOME/webapps/*

COPY webapp $CATALINA_HOME/webapps/ROOT/
COPY application.properties $CATALINA_HOME/webapps/ROOT/WEB-INF/classes/config/application.properties

COPY run-mimir.sh $CATALINA_HOME/bin

# We run Tomcat as a non-root user - if you intend to mount a folder from the
# host as the /data volume then you may need to edit the "1000" in this section
# to match the numeric UID of the user who owns the relevant host folder.  If you
# don't change the UID here you will need to arrange for your data folder to be
# writeable by UID or GID 1000, and accept that the created indexes will have
# this ownership.

RUN groupadd -g 1000 -o mimir && \
      useradd -u 1000 -g 1000 -ms /bin/bash mimir && \
      chown -R mimir:mimir $CATALINA_HOME && \
      chown -R mimir:mimir /data && \
      install -o 1000 -g 1000 -d /data/db /data/indexes /data/temp
USER 1000:1000

VOLUME /data

CMD ["run-mimir.sh", "run"]
