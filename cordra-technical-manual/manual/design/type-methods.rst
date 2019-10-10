.. _type-methods:

Type Methods
============


Type methods are Cordra's way of enabling custom operations to be added to the system. And these operations or methods
defined in JavaScript can be enabled in the context of a given type; hence the name *Type methods*. This way to
execute JavaScript is in addition to executing JavaScript methods at specific lifecycle points
of the digital object management.


Instance Methods
----------------

JavaScript can be used to associate and define arbitrary named methods which can return information about an object
and/or modify the object. We refer to these as Instance Methods to indicate that these methods can only be invoked
by a client on a specific object instance (using its id).

Necessary object locking is performed by Cordra prior to executing these methods.

Suppose you have an instance of an object that has a property called "name". The below instance method could be
used to retrieve that single property:

.. code-block:: js

    exports.methods = {};
    exports.methods.extractNameInstanceMethod = extractNameInstanceMethod;

    function extractNameInstanceMethod(object, context) {
        return object.content.name;
    }

Instance methods are made available to Cordra by assigning them to the object ``exports.methods``.

The REST API can be invoked using curl. The param ``objectId`` specifies the object
to invoke the method on and ``method`` specifies the method to call. ::

    curl -k -X POST 'https://localhost:8443/cordra/call?objectId=test/abc&method=extractNameInstanceMethod'
    -H 'Authorization: Bearer ACCESS_TOKEN'

The POST body of the method call API is interpreted as JSON and passed to the method as ``context.params``.


Instance methods can also act as an alternative means of object update. Here is an example to update the name property:

.. code-block:: js

    exports.methods = {};
    exports.methods.updateNameInstanceMethod = updateNameInstanceMethod;

    function updateNameInstanceMethod(object, context) {
        object.content.name = context.params.newName;
        return object.content.name;
    }

Request::

    curl -k -X POST 'https://localhost:8443/cordra/call?objectId=test/abc&method=updateNameInstanceMethod'
    -H "Accept: application/json"
    -H "Content-type: application/json"
    -d '{"newName":"some name here"}'
    -H 'Authorization: Bearer ACCESS_TOKEN'

Response::

    "some name here"

Note that ``beforeSchemaValidation`` is not automatically run when updating an object in this manner.
(The ``beforeSchemaValidation`` code could be called directly by the method code if desired.)

Static Methods
--------------

Static methods are are not associated with any particular instance of an object, and are useful for only reading
information from one or more objects of any type. No object locking is performed by Cordra prior to the execution
of these methods.

Since a static method is not associated with an object instance, it only has the single argument called ``context``.
When invoking a static method through the REST API, an optional JSON POST body can be supplied which is made available
under ``context.params``. In the below example, the function echoes back whatever was included in the params
along with a timestamp.

Static methods are made available to Cordra by assigning them to the object ``exports.staticMethods``.

.. _static_method_example:

.. code-block:: js

    exports.staticMethods = {};
    exports.staticMethods.exampleStaticMethod = exampleStaticMethod;
    exports.staticMethods["123/abc"] = exampleStaticMethod;

    function exampleStaticMethod(context) {
        var input = context.params;
        var result = {
            input: input,
            timestamp : new Date().getTime()
        };
        return result;
    }

Request::

    curl -k -X POST 'https://localhost:8443/cordra/call?type=Document&method=exampleStaticMethod'
    -H "Accept: application/json"
    -H "Content-type: application/json"
    -d '{"foo":"hello", "bar":"world"}'
    -H 'Authorization: Bearer ACCESS_TOKEN'

Response::

    {"input":{"foo":"hello","bar":"world"},"timestamp":1532719152687}

The example static method shown in the JavaScript above also demonstrates how a method can be
given a handle as an identifier as well as a name. Here the method is also exported with the
handle "123/abc".
