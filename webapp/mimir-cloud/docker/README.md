Building a Docker image of mimir-cloud
======================================

The files in this directory are intended to help build an image to run
mimir-cloud under Docker.  The supplied Dockerfile is set up to assume the
_unpacked_ WAR file is in a folder named `webapp`.

```
$ mkdir webapp
$ cd webapp
$ unzip ../../build/libs/mimir-cloud-{VERSION}.war
```

With the webapp in place simply use `docker build` as normal:

```
$ docker build -t mimir:6.1-SNAPSHOT .
```

The image will put indexes in a volume mounted at `/data`.  If you intend to
mount a folder from the host system as this volume then you may wish to edit
the `Dockerfile` and change the numeric UID and GID under which Mímir will run,
to ensure the indexes it creates will have the correct file ownership.
