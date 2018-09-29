#!/bin/bash
set -e
BASEPATH="$( cd "$(dirname "$0")" ; pwd -P )"
cd $BASEPATH

# Setup local venv
python3 -m venv venv
# Activate venv
source ./venv/bin/activate
# Update pip and then install devspaces and dependencies
pip install --upgrade pip
pip install --extra-index https://pypi.swarm.devfactory.com cndevspaces -U

echo "Checking devspaces install"

# Configure devspaces
set +e
lsoutput=$(cndevspaces ls)
if [[ $lsoutput = *"devspaces configure"* ]]; then
    echo "Configuring devspaces first run ..."
    cndevspaces configure
fi

cndevspaces collections create --filepath $BASEPATH/devspaces-collection.yaml
cd ../
cndevspaces bind -v --collection liferay-coll --config liferay-conf

