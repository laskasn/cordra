.. _sessions-configuration:

Distributed Sessions Manager
============================

By default, Cordra handles HTTP sessions with an in-memory sessions manager. However, Cordra can be
configured to use an alternate sessions manager. To configure this, add a ``sessions`` section to the Cordra
``config.json`` file.

For example::

    "sessions" : {
        "module" : "module-name-goes-here",
        "options" : {

        }
    }

If a property specifying whether the account is active is configured on the User digital object, 
you will be able to invalidate user sessions by deactivating the user account. See
:ref:`auth-accountActive` for details on how to configure account activation.

Session Managers
----------------

There are currently three session managers supported by Cordra, as defined below.

Common Options
--------------

All session managers take the following options:

===========   ====================
Option name   Description
===========   ====================
module        The name of the module to use. Default is "memory"

timeout       Number of seconds before a session is expired. Default is 30 seconds
===========   ====================


System Memory
~~~~~~~~~~~~~

:Module Name: memory

If no sessions module is configured in ``config.json``, Cordra will use the system memory sessions manager. This sessions
manager does not persist sessions over a reboot. Session information stored in memory will be lost if Cordra is restarted.

These sessions are not distributed, and so can only be used in a single instance deployment scenario.

Apache Tomcat
~~~~~~~~~~~~~

:Module Name: tomcat

With this configuration, Cordra will use the Tomcat's built-in facility for sessions. This is fine for a
single-instance deployment, and can be used for a distributed deployment if Tomcat is configured
for distributed sessions (see :ref:`session-replication` for an example of this).

This should not be used with a default Cordra single-instance installation, which uses Jetty instead of Tomcat.


MongoDB
~~~~~~~

:Module Name: mongodb

The MongoDB sessions manager will store sessions in an external MongoDB system. This module requires
additional configuration under a property "options". Under "options", there must be a parameter called ``connectionUri``,
which is a standard  `MongoDB connection string <https://docs.mongodb.com/manual/reference/connection-string/>`__.
The "options" can also include "databaseName" (defaults to "cordra") and "collectionName" (defaults to "sessions").

For example::

    "sessions" : {
        "module" : "mongodb",
        "options": {
            "connectionUri" : "mongodb://localhost:27017"
        }
    }