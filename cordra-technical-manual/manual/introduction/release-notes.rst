.. _release_notes:

Release Notes
=============

What's New in Cordra v2.0
-------------------------

Please visit `Cordra v2.0 Features <https://www.cordra.org/cordra.html>`__ for the core features offered by
version 2 of the Cordra software.

Below you will find a list of incremental changes made to the software over the last several months.


.. warning::

   Cordra Beta v2.0 versions released after 2018-08-01 include an improved
   JavaScript API which is incompatible with earlier released versions of
   Cordra Beta v2.0.  *If* your Cordra configuration includes schema JavaScript,
   see :ref:`here <legacy-js>` for an upgrade path.

   Cordra users upgrading from early versions of the Cordra Beta v2.0, who
   did not use schema JavaScript (apart from the default User schema JavaScript,
   which will be automatically upgraded if it has not been edited),
   do not in general need to take any action.

   Also, earlier versions of Cordra would return all results to searches with
   pageSize=0.  To restore the former behavior, you can add
   "useLegacySearchPageSizeZeroReturnsAll":true to the Cordra :ref:`design-object`.
   By default a search with pageSize=0 returns the number of
   matched objects but no object content.

   As of Cordra Beta v2.0 versions released after 2019-06-01,
   Kafka-based replication no longer includes payloads in the
   replication messages. If you are using replication and you need payloads to
   replicate there is a boolean property "includePayloadsInReplicationMessages"
   that can be set to true on the Design object.  Note that the current
   implementation of replication with "includePayloadsInReplicationMessages"
   may require special Kafka configuration
   or may not be suitable when there are large payloads.

   Cordra Beta v2.0 versions released after 2019-08-02 only support Elasticsearch version 6 and 7
   as indexing backends. If you have an existing Elasticsearch 5 index, you'll need to upgrade and
   reindex.

   Cordra v2.0.0 and later only support using access tokens to create HTTP
   REST API sessions. If your application uses the cookie-based :ref:`legacySessionsApi`, you will need to
   upgrade to use the new Token API. To restore this former behavior, you can add "useLegacySessionsApi":true
   to the Cordra design object. See :ref:`tokenApi` for details on the new API.

   As of Cordra v2.0.0, all authentication requests must be made over
   a secure HTTPS connection. To allow authentication over insecure channels, you can add
   "allowInsecureAuthentication":true to the Cordra design object.
   
   Cordra v2.0.0 uses memory sessions by default.  If you have a distributed Cordra installation which uses
   Tomcat session replication, you will need to configure the Cordra session manager to use Tomcat-managed sessions.
   See :ref:`sessions-configuration`.


Changes in 2.0.0 release (2019-10-09):
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Add built-in identifier resolution; see :ref:`identifiers` and :ref:`handle-integration`.
* Ensure Cordra object ids are syntactically valid handles.
* Make DOIP listener active on port 9000 by default.
* Add handle values to Cordra object id records for DOIP clients to locate objects. 
* Add ``generateId`` JavaScript lifecycle hook; see :ref:`generateId`.
* Allow handleReference in schemas to refer to objects of any types, or any types except a 
  fixed list, instead of requiring a fixed list of allowed types.
* Allow authentication only over HTTPS by default.
* New options for HTTPS configuration, in particular to allow updating certificate without restart;
  see :ref:`https_configuration`.
* Change default session management to memory sessions; add separate configuration option 
  for using Tomcat-managed sessions. See :ref:`sessions-configuration`.
* Add cordraUtil.js module; see :ref:`cordra_util_module`.
* Prevent MongoDbStorage from storing JSON numbers not representable as MongoDB numbers.
* Make client-supplied requestContext available to JavaScript hooks; see :ref:`requestContext`.
* Add parameter ``filter`` to search and retrieval APIs to allow returning only parts of the objects
  specified by JSON pointers.
* Upgrade dependencies; support Elasticsearch 6 and 7 (but not 5).
* Add :ref:`tokenApi` and deprecate :ref:`legacySessionsApi`.
* Add script to allow easier creation of Handle key pairs.
* Support providing jar files in data/lib and sub-directories.
* New API GET /check-credentials to test authentication whether direct or token/session-based.
* Add batch files, e.g., startup and shutdown, for Windows.
* Update technical manual significantly.


Changes in 2019-06-12 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* :ref:`objectHashing`, which allows hashes of the object content to automatically be included in object metadata.
* Fix bug to ensure that any errors resulting from sessions setup (see :ref:`sessions-configuration`) are visible at startup.
* Prevent creating a digital object with a zero-character, i.e., empty, identifier.  If the use of previous versions of Cordra
  resulted in digital objects with empty identifiers, you can
  delete them with this recovery API call: ``DELETE /objects/?deleteObjectWithEmptyId``.
* Ensure that initial default schemas have appropriate createdOn and modifiedOn metadata.
* Versions (see :ref:`objectVersioning`) are now immutable by default; they can be made mutable by setting
  a Design object flag "enableVersionEdits".
* Improved Cordra software performance.
* Fix bug that in rare cases could allow user and group changes to not be immediately visible to the portion of Cordra
  process that authenticates users.
* Fixes to migration from Cordra v1.
* UI fix to prevent issues with schemas containing spaces.
* Allow configuration of cookies used for Cordra sessions; see :ref:`design-object`.
* Kafka-based replication no longer includes payloads in the
  replication messages. If you are using replication and you need payloads to
  replicate there is a boolean property "includePayloadsInReplicationMessages"
  that can be set to true on the Design object.  Note that the current
  implementation of replication with "includePayloadsInReplicationMessages"
  may require special Kafka configuration
  or may not be suitable when there are large payloads.
* Storage modules "custom" and "multi"; see :ref:`storage-configuration`.
* To facilitate clients passing contextual information to the storage backend, HTTP API calls admit a
  query parameter "requestContext".  This will be made available to the instance of StorageChooser used
  by the "multi" storage module.  See :ref:`multiStorage`.

Changes in 2019-04-09 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Fix bug which prevented starting additional webapps in data/webapps.
* Add new config.json property reindexing.async; reindexing.priorityTypes no longer causes async reindexing automatically.  See :ref:`reindexing`.
* Improve documentation around possible issues reindexing when using types like JavaScriptDirectory.

Changes in 2019-03-29 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Ensure that sources of internal CNRI libraries are included in distribution.
* Allow minRf to be configured in Solr indexer configuration.
* Fix client tools ExportByQuery and ImportObjects which can now optionally connect to a MongoDB backend for internal metadata.
* New server-side tools "export-tool" and "import-tool" which can connect directly to Cordra storage in order to export and
  import objects; also "ids-by-query" to retrieve a list of ids from a running Cordra.  See :ref:`import_export`.
* Improve performance of reindexing under Elasticsearch.
* Make it so that components of Cordra object "metadata" are indexed under fields with names like "metadata/createdOn", etc.
* New MongoDB storage configuration option "maxTimeMsLongRunning", which defaults to a large value, to prevent processing
  timeouts on slow reindexing operations.
* New HTTP API for searches which returns only object ids instead of full objects, using query parameter "&ids".
* Fixed bug causing incorrectly sorted search results when using MongoDB storage.
* Fixed bug causing metadata "createdOn" and "modifiedOn" to differ for a newly created object.

Changes in 2019-03-09 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Substantial changes to UI.
* Configurable session management backend; see :ref:`sessions-configuration`.
* User schemas can include flags to activate/deactivate users; see :ref:`auth-accountActive`.
* Single-instance Cordra installation allows additional jar files to be made available to Cordra by placing in data/lib directory.
* A file setenv.sh next to startup will be run by startup (for ease of setting environment variables in automatic installations).
* Remove all internal dependence on objatt\_ fields in the index.  This allows ignoring those fields in a Solr or Elasticsearch install, if desired to save index disk space.
* Schemas can indicate that certain fields should not be stored or retrievable plain, but instead stored as a hash and salt which can be validated.  Useful for secure tokens.  See :ref:`secureProperty`.
* New API GET /startupStatus to indicate when startup has partially failed; intended to be used in situations where HTTP access to Cordra is much easier than checking logs.
  See :ref:`startup-status`.
* Upgrade Jetty backend in single-instance install; now supports HTTP/2 in Java 9 or later.
* /uploadObjects API now should use POST rather than PUT.
* GET /acls now only requires read permission.

Changes in 2019-01-31 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* New objectForIndexing JavaScript hook to adjust how the object is indexed; see :ref:`objectForIndexingExample`.
* Required properties with schema ``cordra.type.autoGeneratedField``
  were previously populated only if present with some value, even the
  empty string; now they are auto-generated even if missing.
* Changed default value of reindexing configuration property ``batchSize`` to 16, which allows better
  performance with the default ``"lockDuringBackgroundReindex": true``.
* Fixed UI bug which prevented saving objects with missing but not
  required enum and boolean properties.
* In the UI, the admin schema editor now allows editing schema JavaScript.
* In the UI, added and edited schemas are now usable immediately
  instead of requiring a page refresh.
* MongoDB storage now allow configuration of databaseName, collectionName, and gridFsBucketName.
* Fixed bug which could cause schemas to be unknown to Cordra
  after a reindex in certain configurations.

Changes in 2019-01-11 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Improvements to logging of reindexing, and speed of reindexing when using MongoDB storage.
* UI fix to prevent possible XSS in use of Toastr to show error messages.
* Configurable ACLs for schema methods; see :ref:`authorizationSchemaMethods`.

Changes in 2018-12-06 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Background reindexing fix to ensure objects are (by default) locked during reindexed; see :ref:`reindexing`.
* New /reindexBatch API; see :ref:`reindex_batch_api`.
* Update documentation for /uploadObjects API.

Changes in 2018-11-27 beta release:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* General performance improvements.
* Cordra authorization now allows groups to include other
  groups. Note: to make use of this feature, existing deployments will
  need to edit the Group schema to allow the "users" property to
  include handles of objects of type "Group" as well as type "User".
* Reindexing performance improvements and new configuration options; see :ref:`reindexing`.
* Ensure "Content-Type: application/json; charset=UTF-8" in more responses.

Version 1.0
-----------

* Version 1.0.7 fixes a sporadic classloading issue experienced rarely by some users.

* Version 1.0.6 has several minor bugfixes: HTTPS no longer asks for a client-side certificate;
  Handle resolution is aware of recent GHR
  changes; and the internal implementation of payload indexing is
  streamlined.

* Version 1.0.5 fixes a performance bottleneck in indexing new objects,
  and also includes the full source needed to build Cordra.

* Version 1.0.4 adds HTTP Range requests, as well as the "indexPayloads"
  property to allow turning off indexing of payloads.

* Version 1.0.3 changes how payloads are associated with Cordra objects.
  Now any Cordra object can be associated with zero or more named
  payloads. Payloads are no longer associated with locations in the JSON
  and do not need to be defined in the schema.

* Version 1.0.3 improves handle minting configuration to allow handles to
  redirect to the Cordra UI, the JSON of the Cordra object, payloads of
  the Cordra object, or URLs included in the JSON. There is also a handle
  updater to allow changes to handle records to be performed in bulk.

* Version 1.0.2 includes a bug fix that prevented groups from referencing
  users correctly.
