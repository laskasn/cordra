.. _single_instance_deployment:

Deploying Single-Instance Cordra
================================

There are a number of options available for deploying Cordra. You can learn about those options
:ref:`here <configuration_introduction>`. We describe below the steps needed to start Cordra on a single machine, as a
:ref:`locally-run instance <local-single-deploy>` or within :ref:`Docker <docker-single-deploy>`.

Starting Cordra
---------------

Here are some references to sections within this document that helps with starting Cordra.

Pre-configured Cordra software packages downloaded from the Cordra website include handle
prefix information necessary for allotting unique, resolvable identifiers called handles.

For running a pre-configured Cordra instance:

* locally on a machine, update just the admin password and run Cordra as instructed :ref:`here <local-single-deploy>`.
* as a Docker instance, click :ref:`here <docker-single-deploy-preconfigured>` for details.

For running an un-configured Cordra instance:

* locally on a machine, click :ref:`here <local-single-deploy>` for details.
* via Docker, click :ref:`here <docker-single-deploy>` for details.

.. note::
    The pre-configured Cordra software distribution includes a handle prefix and a necessary key pair
    useful for a quick start. The handle prefix is allotted on a trial basis, and handles are made
    resolvable in the Internet also on a trial basis. We strongly recommend that you update the key pair if
    security is important even during the trial.


Software Distribution Details
-----------------------------

The Cordra software can be downloaded from `here <https://www.cordra.org>`__. There are
two options available: One which is pre-configured to use our hosted identifier (aka handle)
service for a trial that allows digital objects to be locatable in the Internet, and one which
you can configure to use your own handle server at a later point when you deem it is important for your users to auto
locate digital objects. For more information about the Handle technology and setting up a handle server, please see
`here <https://handle.net/download_hnr.html>`__ .

For most users evaluating Cordra, we recommend trialing the hosted handle service by requesting a pre-configured
Cordra software distribution. See `here <https://www.cordra.org/download.html>`__ for details.

.. _local-single-deploy:

Locally Run Instance
--------------------

Before starting Cordra for the first time, the admin password needs to be set in a ``repoInit.json`` file
under the ``data`` directory.  This file should also contain the desired handle prefix, from which the unique
identifiers will be generated; if the prefix is omitted, the prefix ``test`` will be used by default to create objects.

Pre-configured Cordra software distribution downloaded from the Cordra website includes the prefix,
but admin password must be set prior to starting Cordra for the first time.


Example ``data/repoInit.json``::

    {
        "adminPassword": "changeit",
        "prefix": "20.5000.123.TEST"
    }

Once data/repoInit.json is in place, you can start Cordra with the ``startup`` script, and stop it with the ``shutdown``
script.

When Cordra starts, the admin password will be securely stored in the system and the ``repoInit.json`` file will be
deleted. The admin password can then be changed using the web interface while signed in as admin.

Cordra can be started by running the ``startup`` script in the distribution directory. The Cordra web interface will be
available at http://localhost:8080/ and https://localhost:8443. You can sign in as admin in order to further configure your Cordra server, if desired.

Cordra can by stopped by running the ``shutdown`` script.

Deploying Using Apache Tomcat
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Cordra software can be run using a servlet container instead of running in a standalone fashion as described above.
The details below describe how to run Cordra using Apache Tomcat.

The Cordra war file is located in the Cordra distribution at ``sw/webapps-priority/ROOT.war``.  It can be renamed to
``cordra.war``, for instance, if desired.

You should copy the war file into the Tomcat ``webapps`` directory,

The only change needed from a default Tomcat installation is to specify the Cordra data directory. This is done using
Java system properties, which can be set in Tomcat via an environment variable ``CATALINA_OPTS``. This setting can be
set using the file ``bin/setenv.sh`` which can be created as a sibling of ``bin/catalina.sh``. Ensure
that ``bin/setenv.sh`` has the following contents::

   CATALINA_OPTS="-Dcordra.data=/path/to/cordra/datadir ${CATALINA_OPTS}"

``CATALINA_OPTS`` can also be used to specify memory configuration, such as::

   CATALINA_OPTS="-Xmx2G -Dcordra.data=/path/to/cordra/datadir ${CATALINA_OPTS}"

Logging can be configured with a ``log4j2.xml`` file in the ``cordra.data`` directory.

.. _docker-single-deploy:

Docker Instance
---------------

The Cordra distribution comes with the files necessary to build a Docker image that includes the Cordra software
distribution.

There are four environment variables that can be set for the Docker image:

* ``CORDRA_ADMIN_PASS`` (REQUIRED) - password for the admin user
* ``CORDRA_BASE_URI`` - base uri for this Cordra
* ``CORDRA_PREFIX`` - Handle prefix to use
* ``CORDRA_HDL_ADMIN`` - Handle admin for the prefix

These variables can be set on the command line, or in the ``variables.env`` file located in the ``docker`` folder.

To build the Docker image, use the following command::

    docker build -t cordra -f docker/Dockerfile .

You can start Cordra using the following command::

    docker run -it -p8080:8080 -p8443:8443 -p2641:2641 -p9000:9000 --env-file docker/variables.env cordra

To persist the Cordra data directory, the following command can be used.  Note that the ``"$(pwd)"/data`` directory
must exist in order for this command to work::

    docker run -it -p8080:8080 -p8443:8443 -p2641:2641 -p9000:9000 \
            --env-file docker/variables.env \
            --mount type=bind,source="$(pwd)"/data,target=/opt/cordra/data \
            cordra

.. _docker-single-deploy-preconfigured:

Pre-configured Cordra Distribution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you have downloaded a pre-configured Cordra distribution from the Cordra website, the package will include
``variables.env`` file with the CORDRA_PREFIX and CORDRA_HDL_ADMIN variables populated. You should edit that file
to fill in values for other environment variables including the admin password.

You can then build and run the Docker image as described above.

Deploying using Docker Compose
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You may also use Docker compose to modify the Docker image built using the above instructions further. This is useful
if you need to include the key pair your Cordra will use, or if you would like to modify the config.json file. There is a
sample ``docker-compose.yml`` configuration file included that shows how to start a single instance of Cordra.

You can start Cordra by running this command from inside the ``docker`` subdirectory::

    docker-compose up


External Indexing and Storage
-----------------------------

Cordra can be configured to use external indexing and storage services by editing the ``data/config.json`` file in the Cordra
distribution. An example might look like this::

    {
        "httpPort": 8080,
        "listenAddress": "0.0.0.0",

        "index" : {
            "module" : "elasticsearch",
            "options" : {
                "address" : "localhost",
                "port"    : "9200",
                "addressScheme"  : "http",
                "index.mapping.total_fields.limit": "2000"
            }
        }

        "storage" : {
            "module" : "s3",
            "options" : {
                "accessKey" : "XXXXXXXXXXXXX",
                "secretKey"    : "XXXXXXXXXXXXX",
                "bucketName"  : "test.cordra.org",
                "s3KeyPrefix": "testing1234",
                "region": "us-east-1"
            }
        }
    }

For more details on configuring external storage and indexing services, see :ref:`storage-configuration` and :ref:`indexing-configuration`.

