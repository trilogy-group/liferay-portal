#!/bin/bash
set -e
BASEPATH="$( cd "$(dirname "$0")" ; pwd -P )"
cd $BASEPATH
source ./venv/bin/activate
pip install --extra-index https://pypi.swarm.devfactory.com cndevspaces -U -q
cd ../

cndevspaces info
cndevspaces exec --container liferay-build
