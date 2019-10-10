.. _api_introduction:

Introduction
============

Cordra provides three different programmable interfaces in addition to a graphical interface for web browsers.

HTTP REST API, Digital Object Interface Protocol (DOIP), and Identifier/Resolution Protocol (IRP) are the three programmable
interfaces. The software distribution includes client libraries for Java, JavaScript, and TypeScript developers.

HTTP REST API
-------------

Cordra provides a RESTful HTTP API for interacting with digital objects. The HTTP REST API reduces the entry barrier
and enables users to leverage Cordra's features using most HTTP client libraries, although the Cordra distribution
itself includes client libraries, both a :doc:`Java version <../client/rest-java>` and a
:doc:`JavaScript/TypeScript version <../client/javascript>`.

Digital Object Interface Protocol (DOIP) Interface
--------------------------------------------------
DOIP is a communication protocol that specifies how clients may interact with digital objects (DOs) that are managed by
DOIP services. The method of such interaction is primarily using identifiers associated with digital objects, including
those that represent operations, types, and clients.

DOIP is an appropriate choice for users who are interested in an architectural style focused on invoking identified
operations, or who focus on persistence or interoperability benefits.

For details about the Cordra's implementation of DOIP along with examples, see :ref:`doip`. For information about
DOIP client library, see :ref:`doip_java_client_library`.

Identifier/Resolution Protocol (IRP) Interface
----------------------------------------------
IRP is a rapid resolution protocol for retrieving state information, such as location, public keys, and digests, of a digital
object from its identifier.

IRP is originally defined under a different name in RFCs 3650, 3651, and 3652. IRP specification, and its evolution, is
currently overseen by the DONA Foundation as part of the `Handle System <https://www.dona.net/handle-system>`__.

Cordra provides an IRP interface enabling clients to rapidly resolve digital object identifiers to their state information.
You may access IRP client libraries `here <http://handle.net/client_download.html>`__.

See :ref:`identifiers` and :ref:`handle-integration` for more information about configuring Cordra identifiers and
its IRP interface.