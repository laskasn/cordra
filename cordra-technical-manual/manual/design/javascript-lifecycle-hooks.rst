.. _javascript-lifecycle-hooks:

Lifecycle Hooks
==========================

Cordra validates incoming information against schemas as defined in the Type objects. Additional rules that validate
and/or enrich the information in the object can be configured to be applied by Cordra at various stages of the object
lifecycle. Such rules are expected to be specified in JavaScript and to be bound to the following lifecycle points:

* before an object is schema validated;
* during id generation;
* before an object is indexed, allowing the object that is indexed to differ from the one that is stored;
* during handle record creation;
* after an object has been retrieved from storage, but before it is returned; and
* before an object is deleted, to forbid deletion under some circumstances.

Lifecycle hooks, in our parlance, are the points of entry for your own JavaScript-based rules that Cordra processes.
In addition to the lifecycle hooks that are discussed in detail below, Cordra enables clients to invoke other rules
in an ad hoc fashion using :ref:`type-methods`.

Currently, various lifecycle hooks are enabled in Cordra for different actions: create, update, retrieve, and delete.

The following diagrams illustrate hooks that are enabled during various stages of the object lifecycle.

.. figure:: ../_static/lifecycle/cordra-create-lifecycle.png
        :align: center
        :alt: Create Lifecycle

.. figure:: ../_static/lifecycle/cordra-update-lifecycle.png
        :align: center
        :alt: Update Lifecycle

.. figure:: ../_static/lifecycle/cordra-retrieve-lifecycle.png
        :align: center
        :alt: Retrieve Lifecycle

.. figure:: ../_static/lifecycle/cordra-delete-lifecycle.png
        :align: center
        :alt: Delete Lifecycle

.. raw:: latex

    \clearpage

Using Hooks in JavaScript
-------------------------

Hooks in a Type Object
~~~~~~~~~~~~~~~~~~~~~~

Most lifecycle hooks are available for use as part of the JavaScript associated with each Type object. This means if you
want to leverage these hooks for multiple types of objects, then you will need to edit the JavaScript for each of the 
types.  See :ref:`using-external-modules` for a method to share code among multiple types.

Here is the shell of the hooks that are available in each Type, which will be explained below.

.. code-block:: js
   :linenos:

    var cordra = require('cordra');
    var cordraUtil = require('cordraUtil');
    var schema = require('/cordra/schemas/Type.schema.json');
    var js = require('/cordra/schemas/Type');

    exports.beforeSchemaValidation = beforeSchemaValidation;
    exports.objectForIndexing = objectForIndexing;
    exports.onObjectResolution = onObjectResolution;
    exports.beforeDelete = beforeDelete;

    function beforeSchemaValidation(object, context) {
        /* Insert code here */
        return object;
    }

    function objectForIndexing(object, context) {
        /* Insert code here */
        return object;
    }

    function onObjectResolution(object, context) {
        /* Insert code here */
        return object;
    }

    function beforeDelete(object, context) {
        /* Insert code here */
    }

Cordra provides two convenience JavaScript modules that can be imported for use within your JavaScript rules. These
modules allow you to search and retrieve objects, and verify hashes and secrets.  Additional modules allow you 
to retrieve schemas and associated
JavaScript hooks, as discussed :ref:`here <using_cordra_modules>`. You can optionally include these modules in
your JavaScript, as shown on Lines 1-4.

You can also save external JavaScript libraries in Cordra for applying complex logic as discussed
:ref:`here <using-external-modules>`.

Lines 6-9 export references to the four hooks that Cordra enables on a Type object: ``beforeSchemaValidation``, ``objectForIndexing``,
``onObjectResolution``, and ``beforeDelete``. When handling objects, Cordra will look for methods with these names and
run them if found. The methods must be exported in order for Cordra to see them. None of the four methods is mandatory.
You only need to implement the ones you want.

The rest of the example shell shows the boilerplate for the four methods. All four take both an ``object`` and a
``context``. ``object`` is the JSON representation of the Cordra object. It may be modified and returned by
``beforeSchemaValidation``, ``objectForIndexing``, and ``onObjectResolution``.

``object`` contains ``id``, ``type``, ``content``, ``acl``, ``metadata``, and ``payloads`` (which has payload metadata,
not the full payload data). ``content`` is the user defined JSON of the object.

``object`` has the following format::

    {
        "id": "test/abc",
        "type": "Document",
        "content": { },
        "acl": {
            "readers": [
                "test/user1",
                "test/user2"
            ],
            "writers": [
                "test/user1"
            ]
        },
        "metadata": {
            "createdOn": 1532638382843,
            "createdBy": "admin",
            "modifiedOn": 1532638383096,
            "modifiedBy": "admin",
            "txnId": 967
        }
    }

``context`` is an object with several useful properties.

================   =====
Property Name      Value
================   =====
isNew              Flag which is true for creations and false for modifications.
                   Applies to beforeSchemaValidation.
objectId           The id of the object.
userId             The id of the user performing the operation.
groups             A list of the ids of groups to which the user belongs.
effectiveAcl       The computed ACLs for the object, either from the object itself or inherited from configuration.
                   This is an object with "readers" and "writers" properties.
aclCreate          The creation ACL for the type being created, in beforeSchemaValidation for a creation.
newPayloads        A list of payload metadata for payloads being updated, in beforeSchemaValidation for an update operation.
payloadsToDelete   A list of payload names of payloads being deleted, in beforeSchemaValidation for an update operation.
params             The input supplied to a :ref:`type-methods` call.
requestContext     A user-suppled requestContext query parameter.
================   =====


.. _generateId:

Generate Object Id Hook
~~~~~~~~~~~~~~~~~~~~~~~

This hook, that is to be stored in the Design object, is for generating object ids when objects are created. The JavaScript can
be edited by selecting ``Design JavaScript`` from the ``Admin`` menu on the UI. The hook will be bound to the property
design.javascript in the Design object (so it can be edited there too).

The shell for this hook is as follows:

.. code-block:: js

    exports.generateId = generateId;
    exports.isGenerateIdLoopable = true;

    function generateId(object, context) {
       var id;
       /* Insert code here */
       return id;
    }

The flag ``isGenerateIdLoopable`` when set to true tells Cordra that if an object with the same id already exists this
method can be called repeatedly until a unique id is found. If the implementation of generateId was deterministic,
which is to say it would always return the same id for a given input object, the ``isGenerateIdLoopable`` should NOT
be set to true.

.. _createHandleValues:

Create Handle Values Hook
~~~~~~~~~~~~~~~~~~~~~~~~~

This hook is for specifying the handle record that is to be returned when handles are resolved
using handle client tools.  This hook is on the separate Design object property ``design.handleMintingConfig.javascript``,
which can be edited by selecting ``Handle Records`` from the ``Admin`` menu on the UI.  

The shell for this hook is as follows:

.. code-block:: js

    exports.createHandleValues = createHandleValues;

    function createHandleValues(object) {
       var handleValues = [];
       /* Insert code here */
       return handleValues;
    }


Exceptions in Schema JavaScript
-------------------------------

Schema JavaScript may throw exceptions as strings::

    throw "You can't do that.";

If the user requests are issued via the REST API, for beforeSchemaValidation and Type methods calls, this will be
returned to the user as a 400 Bad Request. For onObjectResolution and beforeDelete, this will be returned as 403
Forbidden. For search results where onObjectResolution throws an exception, the corresponding object will be omitted
from the search results (this can affect search results count). Other exceptions will be seen by the user as 500
Internal Server Error.

If the user requests are issued via the DOIP interface, a "bad request" error will be returned
wherever 400 and 403 errors are thrown.

.. _using_cordra_modules:

Cordra Modules
--------------

.. _cordra_module:

Cordra.js Module
~~~~~~~~~~~~~~~~

The builtin Cordra.js module has helpful functions, listed below, that may be useful when writing JavaScript code in Type methods.

**Note:** Lifecycle hooks are triggered when calls are made using the external APIs. Calls made to Cordra using the
helpful functions in the cordra.js module do not trigger any lifecycle hooks.

Search
""""""

Use the search function to find objects in the Cordra index::

    cordra.search(query, pageNum, pageSize, sortFields)

This will return an array (in JSON sense) of Cordra objects matching the query. To get all results for a query, set ``pageNum`` to ``0``
and ``pageSize`` to ``-1``. Caution should be used when requesting all results when the query might match a very large
number of objects. ``sortFields`` is a string which is parsed as the string sent to the search HTTP API.

Note: Former versions of Cordra would return all results with pageSize=0.  To restore this former behavior, you can add
``"useLegacySearchPageSizeZeroReturnsAll":true`` to the Cordra Design object.  By default a search with pageSize=0
returns the number of matched objects but no object content.

Get
"""

Use get to get an object from Cordra by the object ID::

    cordra.get(objectId);

If an object with the given ID is found, it will be returned. Otherwise, ``null`` will be returned.

.. _cordra_util_module:

CordraUtil.js Module
~~~~~~~~~~~~~~~~~~~~


Verify Secret
"""""""""""""

Used to verify a given string against the hash stored for that property::

    cordraUtil.verifySecret(obj, jsonPointer, secretToVerify);

Return true or false, depending on the results of the verification.

Verify Hashes
"""""""""""""

Verifies the hashes on a cordra object property::

    cordraUtil.verifyHashes(obj);

Returns a verification report object indicating which of the object hashes verify.

Hash Json
"""""""""

Hashes a JSON object, JSON array or primitive::

    cordraUtil.hashJson(jsonElement);

Returns a base16 encoded string of the SHA-256 hash of the input. The input JSON is first canonicalized before being hashed.


.. _cordra_schemas:

Cordra Schemas and JavaScript
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Schemas associated with type objects are available to the JavaScript via ``require('/cordra/schemas/Type.schema.json')``,
and JavaScript added to those type objects via ``require('/cordra/schemas/Type')``.  Here `Type` should be replaced
by the name of the particular type to be accessed.



.. _using-external-modules:

Using External Modules
----------------------

External JavaScript modules can be managed with a Cordra object as a payload configured to be a "referrable" source
of JavaScript modules. Typically, this can be done on a single object of a type called JavaScriptDirectory. Here are the
steps needed to create and populate the JavaScriptDirectory object.

#. Create a new schema in Cordra called "JavaScriptDirectory" and using the "javascript-directory" template.
#. Create a new JavaScriptDirectory object. Set the directory to ``/node_modules``. This will allow you to import modules
   by filename, instead of directory path and filename.
#. Add your JavaScript module files as payloads to the JavaScriptDirectory object. The payload name should match the
   filename and will be used when importing a module. For example, a payload named ``util.js`` could be importing using
   ``require('util');``

The use of external JavaScript modules affects reindexing.  It is currently necessary to ensure that objects of type
"Schema" and any sources of JavaScript (like type "JavaScriptDirectory") are indexed first.  See :ref:`reindexing`
for information.

JavaScript Version and Limitations
----------------------------------

Cordra uses the Nashorn JavaScript Engine packaged with Java. The version of JavaScript supported depends on the version
of Java used to run Cordra. Java 8 supports Ecmascript 5.1. As of the time of this writing, Java 9 supports some but not
all Ecmascript 6 features.

In Java, there is a limit on the size of a single function. It is rare to run up against this limit writing Java code,
but it can happen when JavaScript is compiled to Java. This is especially true when using third-party libraries, which
may be minified in one large function. If you hit this limit, you will see the error "Code Too Large" in your logs.

.. _legacy-js:

Legacy JavaScript Hooks
-----------------------

In early versions of the Cordra 2.0 Beta software, the JavaScript hooks ``beforeSchemaValidation``,
``onObjectResolution``, and ``beforeDelete`` took the JSON content of the Cordra object, instead of the full Cordra
object (including id, type, content, acl, metadata, and payloads properties). Additionally the JavaScript ``cordra.get``
function returned only the content instead of the full Cordra object.

If a Cordra instance with JavaScript written for those earlier versions needs to be upgraded, and it is not yet possible
to adapt the JavaScript to the current API, then the following flag must be added to the Design object::

   "useLegacyContentOnlyJavaScriptHooks": true

For more information on editing the Design object, see :ref:`design-object`.

Cordra users upgrading from early versions of the Cordra 2.0 beta, who did not use schema JavaScript (apart from the
default User schema JavaScript, which will be automatically upgraded if it has not been edited), do not in general need
to take any action.


Examples of Hooks
-----------------

.. _userSchemaJsExample:

Example: User Schema JavaScript
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The default Cordra User schema comes with JavaScript that performs basic password validation.

.. code-block:: js

    var cordra = require("cordra");

    exports.beforeSchemaValidation = beforeSchemaValidation;

    function beforeSchemaValidation(object, context) {
        if (!object.content.id) object.content.id = "";
        if (!object.content.password) object.content.password = "";
        var password = object.content.password;
        if (context.isNew || password) {
            if (password.length < 8) {
                throw "Password is too short. Min length 8 characters";
            }
        }
        return object;
    }

This code will run before the given object is validated and stored. If this request is a create
(``context.isNew`` is true) or contains a ``password``, the password is checked to make sure it is long enough. If not,
an error is thrown. This error will be returned to the callee and can be displayed as desired.

Example: Document Modification
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In this slightly more complicated example, we will bind lifecycle hooks to the Document type pre-defined in Cordra with
the following features:

* Add a timestamp to the description of the document in a way it is stored.
* Add a timestamp to the description when the object is resolved, but not actually store.
* Require that the description be changed to "DELETEME" before the document can be deleted.

To demonstrate loading JavaScript from an external file, the function to create the timestamp is in a file called
``util.js``. Create a JavaScript Directory (as described above) and upload this file as a payload named ``util.js``.

.. code-block:: js

    exports.getTimestampString = getTimestampString;

    function getTimestampString(isResolution) {
        var currentDate = new Date();
        if (isResolution) {
            return '\nResolved at: ' + currentDate;
        } else {
            return '\nLast saved: ' + currentDate;
        }
    }

Next, edit the Document type in Cordra and put the following in the JavaScript field.

.. code-block:: js

    var util = require('util');

    exports.beforeSchemaValidation = beforeSchemaValidation;
    exports.onObjectResolution = onObjectResolution;
    exports.beforeDelete = beforeDelete;

    function beforeSchemaValidation(object, context) {
        if (object.content.description !== 'DELETEME') {
            object.content.description += util.getTimestampString(false);
        }
        return object;
    }

    function onObjectResolution(object, context) {
        object.content.description += util.getTimestampString(true);
        return object;
    }

    function beforeDelete(object, context) {
        if (object.content.description !== 'DELETEME') {
            throw 'Description must be DELETEME before object can be deleted.';
        }
    }

Finally, create a new document in Cordra. You should see that whenever the document is updated, a new timestamp is
appended to the description. If you view the document's JSON, you should see a single resolution timestamp, which
changes on every resolution. Finally, if you try to delete the document without changing the description to "DELETEME"
you should see an error message.



.. _objectForIndexingExample:

Example: Modification of the Indexed Object
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

It is possible make changes to the object that is indexed such that it differs from the object that is stored. This is
achieved by writing a function called ``objectForIndexing``.

.. code-block:: js

    exports.objectForIndexing = objectForIndexing;

    function objectForIndexing(object, context) {
        if (object.content.name == "foo") {
            object.content.otherName = "bar";
        }
        return object;
    }

In this example if the incoming object has a property called ``name`` with the value ``foo``, a new property will be
added to the indexed object called ``otherName`` with the value ``bar``. The object that is stored with not contain
the new property but you will be able to search for this object via this property with the query ``/otherName:bar``.

.. _generateIdExample:

Example: Generating ID
~~~~~~~~~~~~~~~~~~~~~~

Example JavaScript for generating object ids is shown below. Here we generate a random suffix for the handle in base16
and append it to a prefix. By setting ``isGenerateIdLoopable`` to true, we ask Cordra to repeatedly call this method
until a unique id is generated.

.. code-block:: js

    var cordra = require('cordra');

    exports.generateId = generateId;
    exports.isGenerateIdLoopable = true;

    function generateId(object, context) {
        return "test/" + randomSuffix();
    }

    function randomSuffix() {
        return Math.random().toString(16).substr(2);
    }

.. _createHandleValuesExample:

Example: Creating Handle Values
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Example JavaScript for creating handle values is shown below. The JavaScript puts a copy of the information from the
Cordra object in the Handle record.

.. code-block:: js

    exports.createHandleValues = createHandleValues;

    function createHandleValues(object) {
        var handleValues = [];
        var dataValue = {
            index: 500,
            type: 'CORDRA_OBJECT',
            data: {
                format: 'string',
                value: JSON.stringify(object.content)
            }
        };
        handleValues.push(dataValue);
        return handleValues;
    };
