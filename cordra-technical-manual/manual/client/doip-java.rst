.. _doip_java_client_library:

DOIP Client Library - Java Version
==================================

The Cordra software includes the reference implementation software for the
`DOIP v2 specification <https://www.dona.net/sites/default/files/2018-11/DOIPv2Spec_1.pdf>`__, as provided by the
DONA Foundation. Much of this DOIP documentation is based on the documentation provided by the DONA Foundation.

The DOIP client software can be used to interact with Cordra's DOIP Interface.

Location
--------

The DOIP client library can be included in your Gradle or Maven build files.

Gradle::

    compile group: 'net.dona.doip', name: 'doip-sdk', version: '2.1.0'

Maven::

    <dependency>
        <groupId>net.dona.doip</groupId>
        <artifactId>doip-sdk</artifactId>
        <version>2.1.0</version>
    </dependency>


The client library and its dependencies can also be found in this directory in the downloaded package::

    cordra/sw/lib/


Background
----------

The DOIP client provides both TransportDoipClient, which includes Java classes that closely reflect the structures of
the DOIP v2.0 specification, and DoipClient, which uses TransportDoipClient to expose a Java API that maps more directly
with the conceptual usage of the DOIP with one Java method per basic operation, and one more for invoking any extended
operation.

We only discuss the DoipClient here. Please build Javadoc for the Cordra software and refer to it for details on
TransportDoipClient.

DoipClient
----------

This client serves two purposes. First, it provides a Java API to invoke the basic operations as well as any extended
operations, abstracting away the protocol-specific serialization details. And secondly, it uses an
Identifier/Resolution mechanism, in particular the Handle System, to discover where to send DOIP requests at request
time using the target Id of the digital object being interacted with. Additionally, the client maintains connection
pools for the DOIP services being interacted with.

Constructing an instance of the DoipClient is done with a no-args constructor. There is no need to specify the IP address
or port of the DOIP server to be communicated with as that information is discovered automatically when the request is
made. However, should there be a need to explicitly instruct the client to communicate with a specific DOIP service,
ServiceInfo can be optionally supplied with each request.

An example to create a digital object is shown below:

.. code-block:: js

    DoipClient client = new DoipClient();
    AuthenticationInfo authInfo = new PasswordAuthenticationInfo("admin", "password");

    DigitalObject dobj = new DigitalObject();
    dobj.id = "35.TEST/ABC";
    dobj.type = "Document";
    JsonObject content = new JsonObject();
    content.addProperty("name", "example");
    dobj.setAttribute("content", content);

    Element el = new Element();
    el.id = "file";
    el.in = Files.newInputStream(Paths.get("/test.pdf"));
    dobj.elements = new ArrayList<>();
    dobj.elements.add(el);

    ServiceInfo serviceInfo = new ServiceInfo("35.TEST/DOIPServer");

    DigitalObject result = client.create(dobj, authInfo, serviceInfo);


ServiceInfo
~~~~~~~~~~~
The ServiceInfo contains information specifying where to send the request. This can be a handle that resolves to a
handle record that contains a value for type 0.DOIP/ServiceInfo, the structure of which is defined in the DOIP specification.
Alternatively, the ServiceInfo could directly contain the IP address, port and service id of the target service. In the above
example a handle is supplied to direct the client to the service that the DigitalObject should be created at.

A call to retrieve a DigitalObject with identifier that resolves to the service information would look like this:

.. code-block:: js

    DigitalObject result = client.retrieve("35.TEST/ABC", authInfo);

Here, only the Id of the object is passed into the call and the ServiceInfo is discovered by the client. Other
operations that apply to existing objects could be invoked without supplying ServiceInfo.

However, ServiceInfo can be supplied specifically as shown in the below example:

.. code-block:: js

    ServiceInfo serviceInfo = new ServiceInfo("35.TEST/DOIPServer", "10.0.1.1", 8888);
    DigitalObject result = client.retrieve("35.TEST/ABC", authInfo, serviceInfo);

AuthenticationInfo
~~~~~~~~~~~~~~~~~~
Three classes are provided that can be used to send authentication information to the DOIP server.

* PasswordAuthenticationInfo, which sends a username and password.
* PrivateKeyAuthentictionInfo, which given a Privatekey, will generate and send a JSON Web Token (RFC 7519).
* TokenAuthenticationInfo, which given a (any) token, will send it with the request.

Basic Operations
~~~~~~~~~~~~~~~~
Methods are provided that support the 7 basic operations.

==========================  =============
Operation	                Method name
==========================  =============
0.DOIP/Op.Hello	            hello
0.DOIP/Op.Create	        create
0.DOIP/Op.Retrieve	        retrieve, retrieveElement
0.DOIP/Op.Update	        update
0.DOIP/Op.Delete	        delete
0.DOIP/Op.search	        search, searchIds
0.DOIP/Op.ListOperations	listOperations
==========================  =============


DigitalObject
~~~~~~~~~~~~~
DigitalObject is a Java class that represents the structure of a Digital Object as defined in the DOIP v2 specification.
The DigitalObject Java class contains the id, type, attributes and elements. The create, update, retrieve and search
methods make use of DigitalObject in their arguments and return types.

Search
~~~~~~
Two search methods are provided, one that returns DigitalObject instances, and one that only returns the Ids of the
objects that match the query: search() and searchIds() respectively. Both return an object of type SearchResults which
implements Iterable.

.. code-block:: js

    QueryParams queryParams = new QueryParams(0, 10);
    String query = "type:Document";
    try (SearchResults<DigitalObject> results = client.search("35.TEST/DOIPServer", query, queryParams, authInfo)) {
        for (DigitalObject result : results) {
            System.out.println(result.id + ": " + result.type);
        }
    }

The number of results returned are limited by what was specified in the request or what the server deems appropriate.
QueryParams is used in this example to specify the server to return 10 results from offset 0. SearchResults is
Autocloseable, and so can be used in a try-with-resources statement. Without a try-with-resources statement,
SearchResults must be explicitly closed at the end of results processing in order to release the connection to the server
back to the pool of connections managed by this client library. SearchResults is also Iterable, and so can be used in a
for-in loop.


performOperation
~~~~~~~~~~~~~~~~
All of the basic operations use the method performOperation() internally. This method is made
public (in a Java sense) such that you can send extended operations.

The performOperation method takes an InDoipMessage and returns a DoipClientResponse. These classes and their use are
described in the Javadoc.
