Build and run with Docker
=========================

To build and run with Docker alone, please use the following scripts:

- ./docker-build.sh to build the image locally
- ./docker-run.sh to run a container

Once inside the container, you can build Liferay normally:

- cd into the /data folder, which gets mapped to your local liferay directory
- create a file named app.server.root.properties, and write inside "app.server.parent.dir=/outputfolder"
- run "ant all"
- find in /outputfolder the results

After this you can run Liferay:

- cd /outputfolder to go into the folder where the build artifacts were placed
- ./tomcat-9.0.10/bin/catalina.sh run to run tomcat with Liferay
- point your browser to http://localhost:18080/


Build and run with Docker compose
=================================

Instead of using the above scripts, you can use Docker compose to actually build and run the container:

- docker-compose up -d 
- docker-compose exec main bash

Build and run on DevSpaces
==========================

If not already done, build and publish the docker image using the following scripts:

- ./docker-build.sh to build the image locally
- ./docker-publish.sh to publish the image on devfactory docker repository

Once this is done, the following scripts will take care of all the steps:

- ./cndevspaces-setup.sh will install DevSpaces if not already present, configure it locally, create the collection 
and bind the local directory. It needs to be run once, or after the collection gets deleted.
- ./cndevspaces-login.sh logs you in the remote DevSpace. Once logged in, you can follow the same instructions above
to build (ant run) and run (catalina run) Liferay.
- local changes will be automatically synchronized and uploaded to the DevSpace, while build artifacts and logs will 
be synchronized back to the local machine.
- once done, simply type "exit" to return to local command prompt
- if you're not working anymore on Liferay, or if you want to recreate the collection, tou can use 
./cndevspaces-delete.sh to delete it.



