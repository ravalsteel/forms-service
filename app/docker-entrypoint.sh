#!/bin/sh
set -eu
# Named volume is created root-owned; the app user must be able to write exports.
storage="${FILE_STORAGE_ROOT:-/var/lib/forms-files}"
mkdir -p "$storage"
chown -R forms:forms "$storage"
exec su-exec forms java -jar /app/app.jar
