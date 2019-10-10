.. _reindexing:

Reindexing
==========

Occasionally it is useful to cause Cordra to recreate its index from
its storage.  For example, if externally managed Solr or Elasticsearch
configuration is changed, then a reindex will cause objects to be
searchable according to the new configuration.

The procedure to cause a reindex is:

#. Shut down all instances of Cordra.
#. Delete all objects from the index.  For Lucene, this can be done by
   deleting the directory "cordraIndex" in the Cordra data directory.  For
   Solr or Elasticsearch, this can be done through the index's own
   administrative API or UI.
#. Start any instance of Cordra.

Some configuration options for reindexing can be changed in Cordra's config.json,
in a subobject "reindexing"::

    {
        "reindexing": {
            "numThreads": 32,
            "batchSize": 16,
            "logChunkSize": 10000,
            "logProgressToConsole": false
        }
    }

The values given are the defaults.

* "numThreads" specifies the number of threads used for reindexing.
* "batchSize" specifies the number of objects indexed per individual request to the indexing service.
  Together, "numThreads" and "batchSize" can be tuned for performance.
* "logChunkSize" specifies how frequently reindexing progress should be logged.
* "logProgressToConsole": if true, reindexing progress will be logged to the console in addition
  to Cordra's error.log.

By default Cordra will reindex all objects before startup is completed.
In some applications, it may preferred to index only certain necessary
types first, and in some cases, to allow startup to complete, and then reindex remaining objects in
the background.  Search will be degraded during the background index,
returning incomplete results.

To require objects of certain types to be indexed first, the property ``"priorityTypes"`` can be
set in Cordra's config.json under "reindexing".  For example::

    {
        "reindexing": {
            "priorityTypes": [ "Schema", "User", "Group", "JavaScriptDirectory" ]
        },
        "isReadOnly": false,
        "index": {
            "module" : "solr",
            ...
    }

Types, users, and groups should generally be reindexed synchronously.

If references to JavaScript modules using types like "JavaScriptDirectory" are used
(see :ref:`using-external-modules`),
it is currently necessary to ensure that objects of type "Schema" and any sources of JavaScript
(like type "JavaScriptDirectory") are indexed first, using priorityTypes.

To enable background reindexing of other types, the boolean property ``"async"`` can be set.
For example::

    {
        "reindexing": {
            "priorityTypes": [ "Schema", "User", "Group", "JavaScriptDirectory" ],
            "async": true
        },
        "isReadOnly": false,
        "index": {
            "module" : "solr",
            ...
    }

If async is enabled, then by default, Cordra will lock objects during
the background reindex of non-priority types.  This prevents an object from
being updated at the same moment it is reindexed, which is needed to ensure
that storage and index do not become out-of-sync.  In some situations, though,
this is not necessary; for example, when Cordra objects are only created
and never updated, or when Cordra is only accessible read-only during the
reindex.  In such cases the performance of the background reindex can be
improved by setting "lockDuringBackgroundReindex": false::

    {
        "reindexing": {
            "priorityTypes": [ "Schema", "User", "Group", "JavaScriptDirectory" ],
            "async": true,
            "lockDuringBackgroundReindex": false
        }
    }

For optimal performance, we also recommend increasing the "batchSize" to 100,
when "lockDuringBackgroundReindex".
