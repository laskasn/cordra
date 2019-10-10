.. _objectLinking:

Digital Object Linking
======================

Digital objects in Cordra can be linked together in a verifiable way by combining digital object identifiers and
digital object hashes. For details about hashing digital object, see :ref:`objectHashing`. Consider that we want to
create a sequence of blocks (Block objects), each of which references one or more Book objects. Specifically, say, the
following linked digital objects are desired::

    test/second-block
        |
        +-->test/othello
        |
        +-->test/first-block
                |
                +-->test/macbeth
                |
                +-->test/hamlet

The following conceptual schema for a type called Block will be used to enable the above desire: Each block contains a
property ``previousBlock`` that includes a reference to the previous block. Additionally each block contains the property
``pointers`` which is an array of references to other Cordra objects of any type from this block. In this
context, a pointer or a reference is a JSON that includes an ``id`` and a ``hash``, both pertaining to
the digital object being pointed at.

You can download the above types and sample digital objects used in this tutorial
:download:`here <../samples/object-linking-example.json>`. You can then load this information using the Cordra web interface.
To do that, sign into Cordra as ``admin``, select the Admin->Types dropdown menu, and click the "Load from file" button. In the
dialog that pops up, select the types file you downloaded and check the box to delete existing objects. Click "Load"
to import the types into Cordra. You can then view the digital objects in Cordra as they are discussed below.

If you view the type digital object for the types Block and Book, you will see that the types have the ``hashObject`` property
set to ``true``. This means that the Cordra software will automatically generate hashes for the objects when they are
created.

Let us say the id and hash for each of the three Book objects are as follows:

.. code-block:: json

    {
      "id": "test/macbeth",
      "hash": "ef8491742fe830636b952e457f168b38f61440bdd9ff8b473765e1114721d63d"
    }
    {
      "id": "test/hamlet",
      "hash": "f4a9a2eb4b0b81dfa227fd80c4e824ad5966d789e23daf7054bd03eed8e37b22"
    }
    {
      "id": "test/othello",
      "hash": "60e7d4998d3bd9ae571c805a03a89e6273bb18883152a1f99a409b65750efacd"
    }

First, we create an initial block. Since this is the first block in the chain, the ``previousBlock`` pointer is omitted.
Two objects are added to the ``pointers`` array, one for ``test/macbeth`` and one for ``test/hamlet``.

Observe in the JSON of the initial Block object below that it contains a ``hashes`` section in the ``metadata``:

.. code-block:: json

    {
      "id": "test/first-block",
      "type": "Block",
      "content": {
        "pointers": [
          {
            "id": "test/hamlet",
            "hash": "f4a9a2eb4b0b81dfa227fd80c4e824ad5966d789e23daf7054bd03eed8e37b22"
          },
          {
            "id": "test/macbeth",
            "hash": "ef8491742fe830636b952e457f168b38f61440bdd9ff8b473765e1114721d63d"
          }
        ]
      },
      "metadata": {
        "hashes": {
          "alg": "SHA-256",
          "content": "705c6e8df77c08749a02466f40b75ca3ca4768c96ca6362fd862a116a62d3d66",
          "full": "c6ff828bc74b5d5ac25b7640dbf3c28d83f6db3643fe6441c5732f0765480518"
        }
      }
    }

To create a second Block, we set its ``previousBlock`` pointer to point at the initial block, including the full hash of
the pointed at object in this object. We also add in a pointer to ``test/othello``:

.. code-block:: json

    {
      "id": "test/second-block",
      "type": "Block",
      "content": {
        "previousBlock": {
          "id": "test/first-block",
          "hash": "c6ff828bc74b5d5ac25b7640dbf3c28d83f6db3643fe6441c5732f0765480518"
        },
        "pointers": [
          {
            "id": "test/othello",
            "hash": "60e7d4998d3bd9ae571c805a03a89e6273bb18883152a1f99a409b65750efacd"
          }
        ]
      },
      "metadata": {
        "hashes": {
          "alg": "SHA-256",
          "content": "ad50778cd9bd0ff72681ef00980b65922756a6133b5bc365441d94a7bf50a5df",
          "full": "1c91dae6ad73281ac0c0ca558f644dd3aefe6b97956f9eeee640c212ee2db0aa"
        }
      }
    }

The following type method on the Block type can be used to verify the chain to ensure that the pointed at objects have
not been updated since the chain has been created.

.. code-block:: js

    var cordra = require('cordra');
    var cordraUtil = require('cordraUtil');

    exports.methods = {};
    exports.methods.verifyChain = verifyChain;

    function verifyChain(block, context) {
        var report = cordraUtil.verifyHashes(block);
        if (!report.full) {
            throw "The hashes on this object " + block.id + " do not verify";
        }
        if (block.content.pointers) {
            for (var i = 0; i < block.content.pointers.length; i++) {
                var pointer = block.content.pointers[i];
                var pointedAt = cordra.get(pointer.id, true);
                if (pointedAt === null) {
                    throw "The object " + pointer.id + " pointed at by " + block.id + " is missing";
                }
                var pointedAtReport = cordraUtil.verifyHashes(pointedAt);
                if (!pointedAtReport.full) {
                    throw "The hash on the pointed at object " + pointer.id + " is invalid";
                }
                if (pointer.hash !== pointedAt.metadata.hashes.full) {
                    throw "The full hash of pointed at object " + pointer.id + " does not match the hash stored in this block";
                }
            }
        }
        if (block.content.previousBlock) {
            var previousBlock = cordra.get(block.content.previousBlock.id, true);
            if (previousBlock === null) {
                throw "Previous block " + block.content.previousBlock.id + " referenced by " + block.id + " is missing";
            }
            var previousBlockReport = cordraUtil.verifyHashes(previousBlock);
            if (!previousBlockReport.full) {
                throw "The hash on the previous block " + previousBlock.id + " is invalid";
            }
            if (block.content.previousBlock.hash !== previousBlock.metadata.hashes.full) {
                throw "The full hash of previous block " + previousBlock.id + " does not match the hash stored in this block";
            }
            return verifyChain(previousBlock, context);
        } else {
            return true;
        }
    }

Invoking this type method will recursively verify the hashes for each pointer against the target object, following the
pointers back to the previous block. Responding with ``true`` if all the hashes verify. If at any point a particular
hash cannot be verified, the method will throw an error with a message indicating which pointer has failed.

Example Invocation::

    curl -k -X POST 'https://localhost:8443/call/?objectId=test/second-block&method=verifyChain' \
      -H "Content-Type: application/json" -H "Authorization: Bearer ACCESS_TOKEN"

