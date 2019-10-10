.. _objectVersioning:

Digital Object Versioning
=========================

This is an experimental feature.

Cordra has support for simple object versioning. Authorized users, i.e., users with write permission, of an object
can request Cordra to create a ``version`` of an existing object. A complete copy of the original object is made
and that copy is given a new unique identifier. The new version is marked with a timestamp for
when it was versioned and the user who performed the operation. The new version also points at the object
that it is made a version of.

Any version of an object should be thought of as a snapshot of the current object at a particular point in time.
By default version objects may not be edited. If you desire to override this behavior and make versioned objects
editable, set the ``enableVersionEdits`` property on the Design object to ``true``.

The Web UI has controls for creating a version of an object and listing existing versions of an object.
