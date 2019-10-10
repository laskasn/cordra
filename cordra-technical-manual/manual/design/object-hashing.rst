.. _objectHashing:

Digital Object Hashing
======================

This is an experimental feature.

Cordra has a feature that when enabled automatically generates hashes of an object and its components on the server when
an object is created or modified.

Hash generation is configured on a per type basis. To enable hashing for a particular type set the ``hashObject`` property
on the schema object to ``true``.

The generated hashes are stored as a JSON object within the object's ``metadata`` property. Each hash is a hex-encoded
string of the bytes using SHA-256.

.. code-block:: json

    {
        "hashes": {
          "alg": "SHA-256",
          "content": "c39e84af6b49c3c6dee72b3d6fa912216f12b8ddb1507f2283ea4a1f5e2f751d",
          "payloads": {
            "payloadName": "c5601d266987ac885ca96522b1b4e439feb7eca39f0fe1111f7342b63b6468f3"
          },
          "full": "8a3251e1a133c65630d0566856f0049b3feb82d0bebc5de58b513c09ccb109ad"
        }
    }

The ``content`` hash is a hash of the JSON content of the object. The JSON is first canonicalized, ensuring consistent
property ordering and value formats.

The bytes of each payload are individually hashed and stored in the ``payloads`` section of the hashes object.

If the object contains ``userMetadata``, the hashes JSON object will contain an additional hash for that component.

The ``full`` hash is a hash over the ``full`` Cordra object including the hashes of any payloads.

It is possible to verify the hashes in server-side JavaScript, such as in lifecycle hooks or Type methods, as follows:

.. code-block:: js

    var cordraUtil = require('cordraUtil');

    var verificationReport = cordraUtil.verifyHashes(cordraObject);

