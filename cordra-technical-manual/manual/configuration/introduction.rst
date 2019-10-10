.. _configuration_introduction:

Introduction
============

Cordra is a highly configurable software system. You can learn about design configuration :ref:`here <design_introduction>`.

There are a number of options available for deploying Cordra. The simplest is a
:ref:`standalone Cordra installation <single_instance_deployment>` that
uses the local filesystem for storage and an embedded indexer. Cordra is configured to run in this mode by default, and
it is a good setup for testing Cordra and any applications built using Cordra.

In addition to configuring Cordra software to use the local file system and embedded indexer, you can also configure a
standalone Cordra installation to use external storage and/or indexing services. Those services would need to be setup
independently, and then have Cordra configured to interact with them. Cordra currently supports the following
backend services:

* Indexing: Apache Lucene (default), system memory Lucene, Elasticsearch, and Apache Solr. Click
  :ref:`here <indexing-configuration>` for details.
* Storage: Filesystem (default), system memory, MongoDB, and Amazon S3. Click :ref:`here <storage-configuration>` for details.

Finally, multiple instances of Cordra can be configured as load-sharing nodes of an application, using external storage
and indexing systems. This setup requires the use of Apache Zookeeper and Apache Kafka to handle coordination between
the nodes. For detailed instructions on setting up a distributed Cordra service, see :ref:`distributed-deployment`.

Management of complex infrastructure requires tools and tutorials related to
:ref:`keys management <https_configuration>`, :ref:`distributed sessions management <sessions-configuration>`,
:ref:`logs management <logs-management>`, :ref:`user management <userManagement>`, :ref:`administrative interface <adminUI>`,
:ref:`import-export tool <import_export>`, and :ref:`environment migration <migration>`.
