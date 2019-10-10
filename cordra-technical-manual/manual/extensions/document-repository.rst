.. _document_repository:

Document Repository
===================

Cordra can be configured to behave like a document repository. In fact, the Cordra distribution is configured this way
by default, and includes definitions for three types of digital objects:

#. Document
#. User
#. Group

You can visit http://localhost:8080/ in a web browser to access the Cordra web interface. Once you login as ``admin``,
you can create digital objects that represent users and groups.

Users can then login to create Document objects, i.e., digital objects of type Document. Cordra will index the metadata as
well as payloads. Queries can then be issued and documents can be retrieved. Document objects can be shared with other
users; likewise, type of access can be restricted to read or write operations. Versions can be created.

All of these UI features can be accessed via the APIs. The :ref:`emr` goes into more details of how to
configure Cordra using schemas, business rules, and access controls. That section also includes examples on how to access
digital objects using the Cordra API.
