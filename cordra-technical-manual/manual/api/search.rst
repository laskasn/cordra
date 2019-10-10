Query Syntax
============

Cordra's APIs enable users to issue queries to search across the managed digital objects based on certain criteria. We
describe below via examples the query syntax to follow to retrieve desired results from queries.

Cordra uses the Lucene query syntax for search. Details of that syntax can be found on the
`Lucene Query Syntax <https://lucene.apache.org/core/2_9_4/queryparsersyntax.html>`__.

The examples below demonstrate the query syntax for fields in the following Cordra object that represents metadata
about the book "Tess of the D'Urbervilles".

.. code-block:: json

    {
      "id": "test/72d1c8508991c7aa0a22362de8574f9c4a0fd28e7ac5bfb4002522b1b7aabafa",
      "type": "Book",
      "content": {
        "title": "Tess of the D'Urbervilles",
        "description": "Tess Durbeyfield is driven by family poverty to claim kinship with the wealthy D'Urbervilles and seek a portion of their family fortune.",
        "author": {
          "firstName": "Thomas",
          "lastName": "Hardy"
        },
        "genre": [
          "Victorian",
          "Tragedy"
        ],
        "publishers": [
          {
            "by": "James R. Osgood, McIlvaine & Co.",
            "date": "1891"
          },
          {
            "by": "Penguin Classics",
            "date": "2003"
          }
        ],
        "language": "English"
      },
      "acl": {
        "writers": [
          "test/xyz"
        ]
      },
      "userMetadata": {
        "Foo": "Bar"
      },
      "metadata": {
        "hashes": {
          "alg": "SHA-256",
          "content": "b919ebb1831b56df5ba4e3b6b649450561efb879ceacb954e0273393e6d9ad95",
          "userMetadata": "424add9fc04ecc6d39b2c12ee958299e93fa55bd29f0b10cb65b2baefaeea402",
          "full": "ddf3a4a1ef7bef667edb467f61ae1bb6de10795b4904a302f3401cef286c5a4d"
        },
        "createdOn": 1562866891119,
        "createdBy": "admin",
        "modifiedOn": 1569513092957,
        "modifiedBy": "admin",
        "txnId": 1569513092948005
      }
    }

The simplest type of query is one that contains one or more terms that are located anywhere in the object.

For example the above object would be included in results for the query::

    Tess

Multiple terms can be combined in a space separated list::

    Tess Durbeyfield Tragedy

These terms are combined with a logical OR such that the above query would match any object that contained any of
these three terms.

Phrase Query
------------

Terms can be grouped together into phrases using double quotes. Here the query::

    "family poverty"

would match to our example object but the query::

    "poverty family"

would not, because the terms in the object are not in the same order as they are in the phrase query.

Note that terms will be tokenized on some non-whitespace characters. For example the query ``foo-bar`` would match all
of foo, bar, and bar-foo as well. If you only wanted to match ``foo-bar``, you must explicitly wrap the query in quotes
to make it a phrase query, e.g. ``"foo-bar"``.

Fielded Queries
---------------

Fields allow the search to be restricted to particular properties of digital object. The JSON schema driven portion of
the object called "content" is fielded using JsonPointers. These are slash separated paths into the JSON tree followed
by a colon and then the term. The following example JsonPointer terms would match the example object::

    /description:family

    /title:Tess

The terms for the properties of sub-JSON-objects are defined with slashes::

    /author/lastName:Hardy

Fielded queries can also be combined with boolean operators::

    /author/lastName:Hardy AND /author/firstName:Thomas

Such a query would only match an object that had both a lastName ``Hardy`` and a firstName ``Thomas``.

Wild card queries
-----------------

A ``*`` character can be used to find results where only part of the term matches::

    /author/lastName:Har*

    /author/lastName:H*y

Note that wildcard queries may not start the value with a wild card. ``/author/lastName:*y`` is an invalid query.

Fuzzy matching
--------------

Fuzzy matching allows for small corrections in spelling mistakes. Here, the below incorrect spelling of Hardy will still
match the example object::

    /author/lastName:Hardi~

    /author/lastName:Hardie~

Fuzzy queries only match terms that are different from the query by at most two characters.

Searching arrays
----------------
In order to explicitly search for the term "Tragedy" within the array property named "genre" the underscore character is
used.::

    /genre/_:Tragedy

In order to search for properties on objects which themselves are in an array, such as the publishers array, e.g.
search for all books with a publisher by "Penguin"::

    /publishers/_/by:Penguin

Range Queries
-------------

To search for objects that have a value that falls between two values is called a range query. The below example shows a
range query on the date field. It will match any value between 2000 and 2004 inclusively::

    /publishers/_/date:[2000 TO 2004]

The same query but excluding the upper and lower bounds uses curly brackets::

    /publishers/_/date:{2000 TO 2004}

Wild cards can also be used to search for anything less than::

    /publishers/_/date:[* TO 2004]

Or anything greater than::

    /publishers/_/date:[2000 TO *]

Note that these values are all treated as text and not numerical values. Less than and greater than refer to
lexicographical ordering.

Query if a property exists
--------------------------

This can be achieved by performing a wildcard range query from any value to any value. The query below will return all
objects that have a property "/language" regardless of the value.::

    /language:[* TO *]

Metadata
--------

Cordra managed metadata of a digital object is also managed as JSON. That ``metadata`` is a sibling of ``content`` within
the Cordra object. Properties within metadata can be searched by property name by prefixing it with "metadata"::

    metadata/createdOn:1562866891119

    metadata/modifiedOn:1562945123652

    metadata/createdBy:admin

    metadata/modifiedBy:admin

    metadata/txnId:1562945123643011

If hashes have been turned on for this type of object those can also be searched on::

    metadata/hashes/full:58848eeda8472a14f4c5fb709aa96094409018b0e623baf7c94c991ea3811f15

Some parts of the metadata can be searched with special field names:

Search by type::

    type:Book

Search by id::

    id:123/test

Search by the user that created or modified the object::

    createdBy:admin

    modifiedBy:admin

Creation and modification timestamps:

The two fields objcreated and objmodified contain the timestamp of the object converted into human readable format
yyyyMMddHHmmssSSS. Note that this field does not contain delimiters. Delimiters can result in tokenization of the string
which can then be challenging to search on::

    objcreated:yyyyMMddHHmmssSSS

    objmodified:yyyyMMddHHmmssSSS

If the object contains userMetadata, as it does in this case, it can be searched with the "userMetadata" prefix::

    userMetadata/Foo:Bar

If explicit acls have been added to the object those can also be searched with the fields ``aclRead`` and ``aclWrite``.
For example searching for all objects that have given explicit write permission to ``test/xyz``::

    aclWrite:test/xyz
