README
======

In this README, we describe how to start a single instance of Cordra Docker.

That said, please refer to the Cordra website for detailed deployment instructions
including instructions to setup a distributed set of load-sharing Cordra instances.
Those instructions are also made available for offline use in the included
Cordra Technical Manual.


Configure and Run
-----------------

Before starting Cordra for the first time, the admin password needs to
be set in a "variables.env" file in the "docker" directory.

To build the docker image, use the following command:

    docker build -t cordra -f docker/Dockerfile .

Now, you can start a single Cordra instance with the following command:

    docker run -it -p8080:8080 -p8443:8443 --env-file docker/variables.env cordra

Cordra should now be available by visiting http://localhost:8080/ in a browser.
You can sign in as admin in order to further configure your server, if desired.
Only users authenticated as "admin" can use the admin interface. Click
the "Sign In" button and sign in as "admin".


Alternative Method
------------------

If you would prefer to use Docker Compose to run Cordra docker, a docker-compose.yml file is
included. This can NOT be used to start multiple scaled instances of Cordra.
You can start Cordra by running this command from insider the "docker" subdirectory::

    docker-compose up


Persisting Data
---------------

To persist the Cordra data directory, uncomment the indicated line in the docker-compose.yml file.

If you are running the docker image directly, you can add a mount to the docker run command. Note
that the "$(pwd)"/data directory must already exist in order for this command to work.

    docker run -it -p8080:8080 -p8443:8443 \
        --env-file docker/variables.env \
        --mount type=bind,source="$(pwd)"/data,target=/opt/cordra/data \
        cordra

If you choose to persist your data, you can remove the admin password from your "variables.env"
file. The admin password will be securely stored in the system. The admin password and prefix can
then be changed using the admin web interface.
