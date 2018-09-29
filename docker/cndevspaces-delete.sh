#!/bin/bash
set -e
BASEPATH="$( cd "$(dirname "$0")" ; pwd -P )"
cd $BASEPATH
source ./venv/bin/activate
set +e

cndevspaces info
cd ../
cndevspaces unbind
cndevspaces collections delete liferay-coll
rm -fR .stfolder

