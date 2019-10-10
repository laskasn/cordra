Introduction to Cordra
======================

Cordra is open source software built on the digital object foundation, and offers a distinct experience to
software developers who intend to build scalable infrastructures for managing digital objects with resolvable
identifiers. This documentation refers to the term *digital object* interchangeably with *Cordra object*, and *object*.

Cordra saves substantial development time as it comes with ready-made functionality that developers desire ranging from
user authentication and access control to information validation, enrichment, storing, and indexing.
Cordra provides this functionality at scale. Most of this functionality can be customized simply via configuration.

Cordra is neither a database nor an indexer. It integrates the two and provides a unified interface. By default,
Cordra is configured to use the local file system of the machine it is running on along with embedded Apache Lucene for
indexing. For building complex applications, such as those that require higher reliability, Cordra supports MongoDB and
Amazon S3 for storage, and Elasticsearch and Apache Solr for indexing.

In some communities, it is important for consumers to be able to locate digital objects from just their identifiers. To
support such cases, the identifiers that Cordra allots are compatible with the Handle System. Cordra
administrators are expected to configure their Cordra identifiers with a prefix (or prefixes) issued by
credentialed parties associated with the Handle System. For details, please refer to :ref:`identifiers` and
:ref:`handle-integration`.

From a developer standpoint, Cordra software simplifies two orthogonal technical issues:
Developing prototype applications and scaling those applications.

Developing Prototype Applications
---------------------------------

Cordra simplifies building prototype applications for managing NoSQL information as digital objects. (The use of
relational databases for storage within Cordra requires user developed extensions.)

For most prototype needs, significant software development is not needed: data models, business rules, and access control
details can be provided as configuration to Cordra. Cordra stitches together the provided configuration with built-in
mechanisms to enable a fully usable information management service, one that authenticates users and applies business
rules in order to protect, store, and index data. Cordra also provides simple and secure REST and DOIP interfaces to
store and retrieve data. The use of DOIP is recommended for applications intended to remain accessible over long time
frames.

Scaling Applications
--------------------

Cordra simplifies scaling a prototype application to meet most production requirements. A collection of
Cordra nodes work in unison to share user demand, while robust machinery to support concurrent access to objects,
execution of operations, and data consistency is enabled internally. Additionally, cross-continent and cross-cloud
replication can be enabled with some configuration and planning.

Overall, Cordra helps manage NoSQL information as digital objects, allots unique, resolvable identifiers, and
enables APIs to interact with those digital objects at scale, while in the backend it is responsible for ensuring
data integrity and durability.

Other Features
---------------

Here are some other specific features of Cordra:

* Validates supplied information against one or more pre-defined JSON schemas, and stores them as digital objects.
* Allots unique, resolvable identifiers (called handles) to managed objects.
* Enables linking between objects, allowing graph of objects; if desired, links can be based on hashes.
* Accepts JavaScript-based rules to apply at various stages of the digital object lifecycle.
* Applies declarative access control policies over managed objects.
* Enables password as well as PKI-based authentication.
* Stores structured records and payloads in the file system, or system memory,
  or in scalable storage services such as Amazon S3 and MongoDB. Support for other services may be added if needed.
* Indexes structured records and payloads in an embedded fashion or via scalable indexing services
  such as Elastic and Solr.
* Includes tools for backend management, such as migration from one service to another.
* Builds a schema-driven, dynamically-generated, web user interface for humans to
  create, retrieve, update, delete, search, and protect objects.
* Provides an administrative web interface for supplying most of the configuration.

Experimental Features
---------------------

* :ref:`objectHashing`: Once properly configured, hashes for digital objects can be automatically generated.
  Verification of hashes as well as immutability checks based on linked digital objects can also be made.
* :ref:`objectVersioning`: Accepts a custom identifier for the version when version object creation is requested.
  Supports a configuration option to specify whether to include payloads when version objects are created.

For a list of possible next steps involving Cordra software, you may refer to the
:ref:`Cordra Design Introduction <design_introduction>` section.