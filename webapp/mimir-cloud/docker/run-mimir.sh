#!/bin/sh

mkdir -p /data/db /data/indexes
cd /data
exec catalina.sh "$@"
