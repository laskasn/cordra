.. _indexing-configuration:

Configuring Indexing Backend
============================

Cordra uses indexers that are based on Apache Lucene, such as Lucene itself, Apache Solr, and Elasticsearch.

By default, Cordra uses Apache Lucene that is configured to use the local file system-based for storing the indexes. However,
Cordra can be configured to use alternate indexing backend systems. It
is mandatory to use an alternate indexing backend system when Cordra is deployed
as a distributed system.

There are a few indexer technologies that Cordra can use for indexing. Cordra includes index modules, which
translate Cordra indexing requirements into what each of the indexer technologies natively offer.

To configure a index module, other than for the default file system based index, add a ``index`` section to the
Cordra ``config.json`` file. For example::

    "index" : {
        "module" : "module-name-goes-here",
        "options" : {

        }
    }

The following index modules are included within the Cordra distribution.


Index Modules
-------------

There are currently four indexing backends supported by Cordra.

Lucene Index
~~~~~~~~~~~~

**Module Name:** ``lucene``

**Module Options:** None

If no indexing backend is configured in ``config.json``, the Cordra will use a
filesystem-based Apache Lucene indexer. This module is only applicable for a
single instance deployment scenario.

System Memory Lucene Index
~~~~~~~~~~~~~~~~~~~~~~~~~~

**Module Name:** ``memory``

**Module Options:**: None

This module uses Lucene, but the index gets erased once the Cordra process is stopped. This module is useful for testing
and is also only applicable for a single instance deployment scenario.

The ``index`` section of the ``config.json`` file looks like this::

    "index" : {
        "module" : "memory"
    }

Elasticsearch Index
~~~~~~~~~~~~~~~~~~~

**Module Name:** ``elasticsearch``

**Module Options:**

=========================   ====================
Option name                 Description
=========================   ====================
address                     Address of Elasticsearch server (Default: ``localhost``)

addressScheme               Protocol for Elasticsearch server (Default: ``https``)

port                        Port number for Elasticsearch server (Default: ``9200``)

baseUri                     URI of Elasticsearch server. If specified, this will be used
                            instead of the previous address settings.

index.*                     Index setting for Cordra index.
=========================   ====================

The Elasticsearch indexer works with both self-hosted instances of Elasticsearch
and Amazon's hosted Elasticsearch service. Cordra currently support Elasticsearch versions 6 and 7.

By default, Cordra sets ``index.mapping.total_fields.limit`` to 10000. You can override
this or send additional index configuration to Elasticsearch by including the appropriate
``index.*`` setting in your configuration. For example, to set the limit on total fields
for the index to 5000, you could use the following configuration::

    "index" : {
        "module" : "elasticsearch",
        "options" : {
            "address" : "localhost",
            "port"    : "9200",
            "addressScheme"  : "http",
            "index.mapping.total_fields.limit": "5000"
        }
    }

Solr
~~~~

**Module Name:** ``solr``

**Module Options:**

=========================   ====================
Option name                 Description
=========================   ====================
baseUri                     URI of Cordra index on Solr indexing server.
                            (Default: ``http://localhost:8983/solr/cordra``

zkHosts                     Connection string for Zookeeper cluster used with SolrCloud

minRf                       A number for the minimum desired replication factor in a
                            SolrCloud configuration.  If the achieved replication
                            is lower Cordra will log a warning.  Generally this will be
                            set automatically based on SolrCloud configuration in
                            Zookeeper; this option can be used to set it lower to
                            prevent warnings when Solr nodes are known to be down.
=========================   ====================

Cordra can be configured to connect to a standalone Solr server or a Solr Cloud cluster
with its configuration stored in Zookeeper. Cordra currently supports Solr versions 6, 7, and 8.

In addition to the Solr setting in the Cordra ``config.json`` file, the following Solr
configuration file updates must be made. The default ``managed-schema`` file (called ``schemas.xml``
on older versions of Solr) should be replaced with the following
(which can be downloaded :download:`here <../solr-cordra-conf/managed-schema>`:

.. include:: ../solr-cordra-conf/managed-schema
    :code:

The default ``solrconfig.xml`` file should be modified in the following ways:

* Change the ``maxTime`` value of ``autoSoftCommit`` to 10000
* Change the ``maxTime`` value of ``autoCommit`` to 60000
* Make sure the ``openSearcher`` value of ``autoCommit`` is set to ``false``
* Remove or comment out the ``searchComponent`` named "elevator" and the ``requestHandler`` named "/elevate"
* Remove or comment out the ``updateRequestProcessorChain`` named "add-unknown-fields-to-the-schema"
* Remove or comment out any ``initParams`` setting that make use of ``add-unknown-fields-to-the-schema``
* In ``initParams``, change the value of ``df`` to ``internal.all``. Any other ``df`` values used should also
  be changed to ``internal.all``.

An example of a fully modified ``solrconfig.xml`` can be downloaded :download:`here <../solr-cordra-conf/solrconfig.xml>`.


Phrase Queries
--------------

You should refer to the query syntax supported by the indexing backend system that you configured with your Cordra instance.

One point is worth noting here. Queries that are placed within double quotes trigger exact match searches. However,
queries without double quotes will be tokenized in a way which can sometimes be surprising.  This is a side effect
of the tokenization used by Lucene, Solr, and Elasticsearch.

For example, suppose you send the query ``/name:foo-bar`` to the indexer.  The
value is tokenized and treated as an OR statement. The query becomes ``/name:(foo bar)``, which will match items with
the name foo, bar, foo-bar, and bar-foo. However, with double quotes, the query is turned into a
phrase query, ``/name:"foo-bar"``, which will only match items with the name "foo-bar".

In general, you should ensure that double quotes are used when a search might result in multiple tokens and only matches of the
entire phrase are desired.
