.. _storage-configuration:

Configuring Storage Backend
===========================

By default, Cordra uses the local file system for storing digital objects. However,
Cordra can be configured to use alternate storage backend systems. It
is mandatory to use an alternate storage backend system when Cordra is deployed
as a distributed system.

There are a few storage technologies that Cordra can use for its storage. Cordra includes storage modules, which
translate Cordra storage requirements into what each of the storage technologies natively offer.

To configure a storage module, other than for the default file system based storage, add a ``storage`` section to the
Cordra ``config.json`` file. For example::

    "storage" : {
        "module" : "module-name-goes-here",
        "options" : {

        }
    }

Storage Modules
---------------

The following storage modules are included within the Cordra distribution:

Filesystem Storage
~~~~~~~~~~~~~~~~~~

If no storage module is configured in ``config.json``, the Cordra will store most information from digital objects
in a local BerkleyDB database and the payloads from those digital objects in a directory on the local filesystem.
The identifiers of the payloads are hashed, and the hashes are used in the storage directory to ensure that payloads
are spread out evenly among the storage sub-directories.

This storage module is only applicable for a single instance deployment scenario.

System Memory Storage
~~~~~~~~~~~~~~~~~~~~~

:Module Name: memory

**Module Options:** none

The ``memory`` storage module uses the system memory; as such the digital objects will be erased once the Cordra process
configured to use the memory module stops. Cordra configured with memory module is useful for testing purposes. There
are no options required to be specified for this storage module.

This storage module is only applicable for a single instance deployment scenario.

The ``storage`` section of the ``config.json`` file looks like this::

    "storage" : {
        "module" : "memory"
    }

MongoDB Storage
~~~~~~~~~~~~~~~

:Module Name: mongodb

**Module Options:**

    ====================   ====================
    Option name            Description
    ====================   ====================
    connectionUri          MongoDB-style connection URI

    maxTimeMs              "maxTimeMs" value used for MongoDB
                           operations, which gives a time limit
                           for processing; default is 30 seconds.
                           Generally this should not need to be
                           set.

    maxTimeMsLongRunning   "maxTimeMs" value used for
                           long-running MongoDB operations used
                           in reindexing; default is 10 days
                           Generally this should not need to be
                           set.

    databaseName           Database name; defaults to "cordra"

    collectionName         Collection name; defaults to "cordra"

    gridFsBucketName       GridFS bucket name (for payload
                           storage); defaults to "fs"
    ====================   ====================

The MongoDB module will store objects in an MongoDB storage system. The ``connectionUri`` is
a standard `MongoDB-style connection string <https://docs.mongodb.com/manual/reference/connection-string/>`_.
If no ``connectionUri`` is configured, the default URI of ``localhost:27017`` will be used.

Amazon S3 Storage
~~~~~~~~~~~~~~~~~

:Module Name: s3

**Module Options:**

    ===========   ====================
    Option name   Description
    ===========   ====================
    bucketName    (required) Name of bucket to use for storage.

    region        (required) AWS region for bucket. (e.g., us-west-2)

    accessKey     (required) AWS access key for user with access to this bucket

    secretKey     (required) AWS secret key for user with access to this bucket

    s3KeyPrefix   Prefix to use for keys on S3 objects.
    ===========   ====================

The S3 module stores Cordra objects in an Amazon S3 bucket. In order to use this
module, you will need to create the bucket on AWS and create a user with full
access to that bucket. An example is below::

    "storage" : {
        "module" : "s3",
        "options" : {
            "accessKey" : "XXXXXXXXXXXXXX",
            "secretKey"    : "XXXXXXXXXXXXXX",
            "bucketName"  : "my-bucket-name.example.org",
            "s3KeyPrefix": "testing1234",
            "region": "us-east-1"
        }
    }


.. _multiStorage:

Multiple Storages
~~~~~~~~~~~~~~~~~

:Module Name: multi

**Module Options:**

    =====================   ====================
    Option name             Description
    =====================   ====================
    storageChooser          (required) Fully-qualified class name of an implementation
                            of net.cnri.cordra.storage.multi.StorageChooser.

    storageChooserOptions   (optional) A JSON object passed to the constructor of the
                            StorageChooser.

    storageMap              (required) A map from String names of storages to
                            storage configurations (with "module" and "options" properties).
    =====================   ====================

This module allows multiplexing among several storage implementations.
A custom implementation of net.cnri.cordra.storage.multi.StorageChooser can be
provided to determine which storage is accessed for each call.

This module can be used if different types of digital objects are to be managed in different storage systems.

For a standard single-instance Cordra deployment, a JAR file containing the class can be
placed in the ``lib`` subdirectory of the Cordra data directory, along with any
dependency JARs (the cordra-core and Gson dependencies will be provided automatically).
If Cordra is deployed in a separate servlet container, the JAR file should be deployed
in the servlet container or in Cordra's own WEB-INF/lib directory.

The StorageChooser can make use of a special feature of the REST API: any call
can take a query parameter "requestContext", which encodes a JSON object.  That
user-supplied context is made available to the methods of the StorageChooser.


Custom Storage
~~~~~~~~~~~~~~

:Module Name: custom

It is possible to create a custom storage module which implements the Java interface.
net.cnri.cordra.storage.CordraStorage.  In addition to ``"module": "custom"``, there
should be a sibling property of ``"module"``, ``"className"``, which should be
set to the fully-qualified name of the Java class implementing CordraStorage.

If the class has a constructor which takes a com.google.gson.JsonObject, the ``"options"``
from the configuration will be passed to that constructor to instantiate the class.
Otherwise a default constructor (taking no arguments) will be called.

For a standard single-instance Cordra deployment, a JAR file containing the class can be
placed in the ``lib`` subdirectory of the Cordra data directory, along with any
dependency JARs (the cordra-core and Gson dependencies will be provided automatically).
If Cordra is deployed in a separate servlet container, the JAR file should be deployed
in the servlet container or in Cordra's own WEB-INF/lib directory.
