.. _rest_java_client_library:

Cordra HTTP REST Client Library - Java Version
==============================================

A Cordra HTTP REST client library in Java for interacting with a Cordra service is included in the distribution. This library
is specifically designed for use with the Cordra's REST API for interacting with Cordra.

Location
--------

The client library can be included in your Gradle or Maven build files.

Gradle::

    compile group: 'net.cnri.cordra', name: 'cordra-client', version: '2.0.0'

Maven::

    <dependency>
        <groupId>net.cnri.cordra</groupId>
        <artifactId>cordra-client</artifactId>
        <version>2.0.0</version>
    </dependency>


The client library and its dependencies can also be found in this directory in the downloaded package::

    cordra/sw/lib/cordra-client/


Background
----------

The various methods supported by the Cordra Java client library are defined in this Java Interface::

    net.cnri.cordra.api.CordraClient


There are two implementations of that Java Interface, one that does not use access tokens and one that does::

    net.cnri.cordra.api.HttpCordraClient
    net.cnri.cordra.api.TokenUsingHttpCordraClient


The basic approach to using Cordra would be to leverage any of the above implementations to perform create, retrieve,
update, and delete (CRUD) operations and other operations. See the code in the aforementioned Java classes for
details on the various supported operations.

That said, the basic digital object Java class stated below should be used for adding, removing, or updating information
prior to invoking any Cordra client operations to commit the changes on the Cordra service::

    net.cnri.cordra.api.CordraObject


Example Usages
--------------

* Create a new instance of CordraClient::

    String baseUri = "http://localhost:8080/cordra/";
    String username = "admin";
    String password = "password";

    CordraClient cordra = new TokenUsingHttpCordraClient(baseUri, username, password);


* Create a digital object of type ``Document`` (without attaching any payload, for brevity sake)::

    JsonObject doc = new JsonObject();
    doc.addProperty("name", "example name");
    doc.addProperty("description", "description");
    CordraObject co = new CordraObject();
    co.id = "test/123";
    co.setContent(doc);
    co.type = "Document";

    co = cordra.create(co);


* Retrieve the digital object::

    CordraObject co = cordra.get("test/123");


* Update the object::

    CordraObject co = cordra.get("test/123");
    co.content.getAsJsonObject().addProperty("name", "updated example name");

    co = cordra.update(co);


* Delete the object::

    cordra.delete("test/123");


* Search for objects::

    try (SearchResults<CordraObject> results = cordra.search("*:*")) {
        for (CordraObject co : results) {
            System.out.println(co.id);
        }
    }

* Invoke a Type method (instance method)::

    JsonElement result = cordra.call("test/123", "exampleInstanceMethod", params, options);

* Invoke a Type method (static method)::

    JsonElement result = cordra.callForType("Member", "getMemberFromUsername", params, options);

