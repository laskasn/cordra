.. _migration:

Migration across Cordra Environments
====================================
There are three scenarios when there may be a need to migrate from one Cordra environment to another:

* Routine upgrade of Cordra software.
* Migrating from Cordra v1 software to Cordra v2.
* Changing deployment infrastructure.

The migration steps needed in each case are discussed below.

.. warning::

    The migration tools included within the Cordra software are designed not to lose data. However, as a precaution,
    it is a good idea to back up your existing data before proceeding with migration.

Routine Upgrade
---------------
If you are using a particular version of the Cordra software, such as v2.x.y, and noticed a new patch or minor release
(i.e., when x and y in the version number has changed), then it should be straightforward to upgrade the Cordra
software. Depending on whether you are using Cordra as a standalone process or running Cordra as a war file within
a servlet container, the steps will be slightly different.

Please pay particular attention to the release notes to notice if any specific migration steps are needed, and follow
those instructions and adjust the instructions that are stated below accordingly:

* You should stop the existing Cordra process (or processes in case of a distributed Cordra system).
* In the case of Cordra war file being run inside a servlet container, replace that war file with the latest war file
  from the new release.
* In the case of Cordra being run as a standalone process, proceed to do a regular installation with the new release
  and just at the point when the Cordra process should be started, copy over the ``data`` directory from the previous
  installation into the new Cordra deployment directory. Also delete the ``repoInit.json`` file from the new Cordra
  environment.
* You may now proceed to start the Cordra process as before.

Migrating from Cordra v1 to Cordra v2
-------------------------------------
If you are using Cordra v1 software, we recommend that you take time to migrate to Cordra v2 software. The steps to do
so are as follows:

* Make a backup copy of the Cordra v1 ``data`` directory.
* Download and unzip Cordra v2.
* Delete the default Cordra v2 ``data`` directory.
* Copy the Cordra v1 ``data`` directory into Cordra v2 installation folder.
* Start Cordra v2.

On startup, the Cordra v2 software will notice that the information in the ``data`` directory is from Cordra v1 and
will migrate all data and configuration as needed. Files that are no longer needed will be moved into a directory named
``data/migration_delete_me``. Once you have confirmed that the migration was successful, you can delete this folder.

After upgrading to Cordra v2, you may wish to move to a distributed system. To do so, simply follow the steps outlined
in the next section.

Changing Deployment Infrastructure
----------------------------------
There may be cases when you intend to change the deployment infrastructure. This could be because you intend to move
from a single Cordra instance setup to multiple Cordra instance setup or vice versa. Or this could be because you
intend to move from one storage technology (say MongoDB) to another (say Amazon S3) and/or from one indexing technology
(say Apache Solr) to another (say Elasticsearch).

Changes to Indexing backend
~~~~~~~~~~~~~~~~~~~~~~~~~~~
If your change in deployment infrastructure does not involve a change to storage backend, but rather a change to just
the indexing backend, then follow the steps outlined below:

* Setup the new indexing backend.
* Stop the Cordra process (or processes in case of a distributed Cordra system).
* Update the Cordra config.json to point at the new indexing backend coordinates. For details, refer to the
  :ref:`indexing-configuration` document.
* Start the Cordra process or processes.
* Issue reindexing. For details, refer to the :ref:`reindexing` document.

Changes to Storage backend
~~~~~~~~~~~~~~~~~~~~~~~~~~
If your change in deployment infrastructure affects the storage backend, we recommend you to export all the digital
objects from your existing Cordra environment and import them into the new environment. For this,

* Ensure the new environment is ready for use.
* In a standalone Cordra scenario, delete the ``repoInit.json`` file from the new Cordra environment.
* In the case of a distributed Cordra system, do not create a znode within ZooKeeper for ``/cordra/repoInit.json``.
* Stop the Cordra process (or processes in case of a distributed Cordra system)
* Export the digital objects using the import export tool from the old environment. For details, refer to the
  :ref:`import_export` document.
* Import the output from the previous step into the new environment. For details, refer to the
  :ref:`import_export` document.
* Start the Cordra process or processes.
* Issue reindexing. For details, refer to the :ref:`reindexing` document.

Special Scenarios and Considerations
------------------------------------

Change Prefix used with Identifiers
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
If you prefer to change the prefix of the identifiers associated with the digital objects, you should export digital
objects, delete the digital objects from Cordra, make modifications on exported files, and import them back into Cordra.

The export tool exports digital objects into a directory called ``objects``. Each file in that directory corresponds to
a single digital object. Except for the payloads managed within the digital objects, the rest of the information is
serialized as JSON that is conducive for editing using text search-and-replace tools such as ``sed``.

Perform a global search for the current prefix and replace them with a new prefix. An example sed command that worked
for us to replace prefix 20.5000.123 with 20.5000.456 is this::

  sed -i 's/20\.5000\.123/20\.5000\.456/g' *

Run the above command from within the ``objects`` directory. Note that payloads are Base64 encoded in each of the
exported file. If there are references to identifiers in those payloads, those identifiers will remain the same as
before.

Other Artifacts
~~~~~~~~~~~~~~~
If you have extended Cordra and loaded additional Java libraries or configuration files into a Cordra environment,
you should remember to specifically copy them into the new environment.
