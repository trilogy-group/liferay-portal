#!/bin/bash

mypath=${BASH_SOURCE%/*}
basedir=$(readlink -f $mypath/..)

echo "Mounting $basedir into /data inside the container"
docker run -p 127.0.0.1:18080:8080/tcp --name liferay-build -v $basedir:/data -d liferay-build-img tail -f /dev/null
docker exec -w /data/ -it liferay-build /bin/bash
