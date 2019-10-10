.. _identifiers:

Identifiers
===========

Cordra allots identifiers to digital objects. Such identifiers are of the form ``prefix/suffix`` (a prefix, a slash, and
a suffix). Identifiers allow digital objects to reference other digital objects, and such linking can become the basis
for enforcing complex business rules.

Cordra provides a built-in Identifier/Resolution Protocol (IRP) interface enabling clients to rapidly resolve
digital object identifiers to their state information, or can be configured to make identifiers resolvable by registering them
at external servers; see :ref:`handle-integration` for details.  What state information to return can be configured using
:ref:`handle-minting-configuration` and :ref:`createHandleValues`.
In some communities, identifiers that are resolvable via the IRP are called ``handles``.

Per IRP, prefixes play a special role. If a prefix is registered with credentialed parties called
`MPAs <https://www.dona.net/mpas>`__, then IRP clients can auto-locate the IRP provider (in this case, a
given Cordra instance) and in turn auto-locate the digital objects managed by that Cordra instance wherever they are in
the Internet.

Cordra APIs for creating digital objects allow users to specify the identifier (including the prefix).
Admins can also specify what identifiers to use when digital objects are created with the help of :ref:`generateId`.
A single Cordra instance may have objects all with the same prefix, or may have objects of multiple different prefixes.
If identifiers are not specified by users or admins, Cordra allots identifiers using a default prefix, which can
be configured as indicated in :ref:`handle-minting-configuration`. The default prefix
(and any other prefixes used) may be registered with MPAs.