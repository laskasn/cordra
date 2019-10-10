.. _payloads:

Payloads
========

A payload, in this context, is information in digital form that is not otherwise maintained in the JSON
portion of the digital object. A file is an example of digital information that can be uploaded to be managed as a payload.
Along with the file, a filename and Internet media type (MIME type) can also be specified.

In Cordra, an object may have zero or more payloads. While :ref:`types` define most aspects of the
information that the object manages, the presence of payloads is not specified in the
JSON schema for an object, although whether or not to index payloads should be specified in that schema
as discussed :ref:`here <indexPayloadsSchemaConfig>`.

Using the REST API, when a digital object is resolved, the full metadata record expressed in JSON is returned
by default. A query parameter to the API request, ``payload``, allows access to payloads by name. When an object
is created or modified, the caller can optionally send a multipart request, containing a part ``content`` with
the full metadata record of the object, and other parts with names being the payload names. Those parts specify
the bytes of the payload file, plus its filename and media type. For a modification, payloads which are not
modified may be omitted. Payloads can be deleted by specifying ``payloadToDelete`` parameters in the request.