.. _import_export:

Import and Export Tools
=======================

Import and Export tools are included in the Cordra distribution. The export tool enables you to extract the digital objects
from a given Cordra setup (single instance or a distributed system) as files into the environment where the tool is run from.
The import tool enables you to ingest the output of an export tool process as digital objects into a (any) Cordra setup. The
rest of this section describes the specifics of these tools.

These tools do not use the Cordra API, but rather use the Cordra storage module to interact with the underlying storage
system. As a result of this, they are able to copy objects in and out of Cordra in their entirety, without modifying their
contents in any way.

The import and export tools can be found in the /WEB-INF/tools directory after unzipping ROOT.war. You can run the
scripts there after unzipping by making the files executable (for instance using chmod +x on \*nix systems).

.. warning::

    Since these tools talk directly to storage they bypass all of the usual validation checks that Cordra makes. For
    example, during import, objects that do not match schemas can be inserted. Likewise, type objects or user objects with
    duplicate names can be inserted. Doing so could result in unexpected and/or unwanted behaviour. Cordra should be
    shutdown before you attempt an import or an export in order to curtail any parallel administrative activity.

When exported, files are produced that represent Cordra objects. Each file contains information from a corresponding Cordra object,
and includes a JSON map of any payloads; and those payloads are encoded as base64 strings. Metadata and schema-driven information
of each Cordra object is represented as JSON in those files. If wholesale changes to digital objects are required, it is easy to
edit the representative files while in the export format, and subsequently import them into Cordra. However, because
payloads are encoded as base64 strings, editing payloads while in the export format is not straightforward.

Once you have imported the objects, Cordra should be re-indexed to function properly. How you reindex depends on the type
of indexer you are using. Deleting the existing index and restarting Cordra will trigger a reindex. For example, if you are
using the default index that comes with Cordra, you can simply remove the ``data/cordraIndex`` directory. For related details,
see :ref:`reindexing`.

The specific commands to import and export are described next, but both import and export tools take the -c option,
which is the path to the config.json that includes the details of the storage being used. In the case of
exporting from the local file system based storage, it is also necessary to specify the path to the Cordra data directory
with the -d option so the tool can find the storage. This option is not needed with MongoDB or Amazon S3 storage.

Export
------

An example to export digital objects from a local file system based Cordra::

    ./export-tool -c cordra/data/config.json -d cordra/data/ -o objects

An example to export from a backend system such as MongoDB or Amazon S3 (and with their coordinates being in config.json)::

    ./export-tool -c config.json -o objects

Import
------
An example to import digital objects into a local file system based Cordra::

    ./import-tool -c cordra/data/config.json -d cordra/data/ -i objects

An example to import digital objects into a backend system such as MongoDB or Amazon S3 (and with their coordinates being in config.json)::

    ./import-tool -c config.json -i objects

.. warning::

    As explained above, a reindexing step is necessary after an import.

Hashed directory output
-----------------------

With large numbers of objects, you may exceed the maximum number of files that your file system allows in a single
directory. In such a case you can use the -t option.
::

    ./export-tool -c cordra/data/config.json -d cordra/data/ -o objects -t

This will hash the object ids and break that resulting hash into segments that are used to create a directory tree. The
import tool does not need to be specified if the import is from a hashed directory or not; it will work either way.

Limit the objects that are exported by id
-----------------------------------------
::

    ./export-tool -c cordra/data/config.json -d cordra/data/ -o objects -i 123/abc -i 123/xyz

Here multiple -i arguments can be passed to the tool to specify which objects to export.

If you have a large number of objects you want to explicitly export, you can list their ids in a new line separated file
and use the -l option.
::

    ./export-tool -c cordra/data/config.json -d cordra/data/ -o objects -l ids.txt

An additional tool called 'ids-by-query' can be used to generate ids file by running a query.
Unlike export-tool and import-tool, it needs to access a running Cordra. This tool comes with the
Cordra distribution and can be found in the ``bin`` directory.
::

    ./bin/ids-by-query -b http://localhost:8080 -u <username> -p <password> -o ids.txt -q <query>

Piping export to import
-----------------------

Instead of writing objects to files in a directory the export tool can instead write objects as new line separated JSON
to standard out using the -s option. This can be piped to the import tool in a \*nix environment.
::

    ./export-tool -c cordra/data/config.json -s | ./import-tool -c cordra2/data/config.json -s
