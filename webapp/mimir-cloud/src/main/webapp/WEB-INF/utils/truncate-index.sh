#!/usr/bin/env bash
#
# Simple script to run the index repair tool - you may need to edit the command
# line to increase or decrease the -Xmx for your system.
#
# IT IS HIGHLY RECOMMENDED TO BACK UP YOUR INDEX BEFORE ATTEMPTING TO REPAIR IT!
# The repair process is potentially destructive, particularly if it crashes part
# way through (e.g. running out of memory).  If this happens you will have to
# restore the index from your backup, fix the problem (e.g. a higher -Xmx) and
# try again.
#
# Usage:
#
#   bash truncate-index.sh [-p /path/to/extra/plugin ...] /path/to/index-NNNNN.mimir
#
# The script will automatically include the plugins that are bundled inside
# this WAR file (in WEB-INF/gate/plugin-cache), if your index refers to classes
# from any other mimir plugins that you have referenced via local configuration
# then you must load those plugins yourself with appropriate -p options - for
# Maven-style plugins use -p group:artifact:version, for directory plugins use
# -p /path/to/plugin or -p file:/url/of/plugin.
#
# The final option on the command line should be the path to the top-level
# directory of the Mimir index you want to repair (i.e. the directory that
# contains config.xml, mimir-collection-*.zip and all the token-N and mention-N
# subdirectories).
#

DIR="`dirname $0`"

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME not set"
  exit 1
fi

CREOLE_CACHE="$DIR/../gate/plugin-cache"

plugins=()

if [ -d "$CREOLE_CACHE" ]; then
  plugins=( -d "$CREOLE_CACHE" )
  while read plug; do
    plugins=( "${plugins[@]}" -p "$plug" )
  done < <( \
    cd "$CREOLE_CACHE" && \
    find * -name \*-creole.jar | \
      sed 's/\/[^\/]*$//' | \
      sort | uniq | \
      sed 's/^\(.*\)\/\([^\/]*\)\/\([^\/]*\)$/\1:\2:\3/' | \
      sed 's/\//./g' \
  )
  # What the above sed pipeline does is:
  # Find all -creole.jars
  # get their parent folders (which are G:A:V of GATE plugins)
  # de-duplicate
  # take the last two path components, which are artifact and version
  # turn remaining slashes to dots to get group
  # 
  # The weird stuff with < <( ... ) is because I can't do the more usual pipe
  # through the "while read" as that would put the plugins=( ... ) in a subshell
fi

"$JAVA_HOME/bin/java" -Xmx2G -classpath "$DIR:$DIR/../lib/*" gate.mimir.util.TruncateIndex "${plugins[@]}" "$@"
