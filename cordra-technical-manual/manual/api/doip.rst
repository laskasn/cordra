.. _doip:

DOIP and Examples
=================

The Digital Object Interface Protocol (DOIP) v2 is a specification of the DONA Foundation.
Please click `here <https://www.dona.net/sites/default/files/2018-11/DOIPv2Spec_1.pdf>`__ for accessing the specification.

DOIP is a communication protocol that specifies how clients may interact with digital objects (DOs) that are managed by
DOIP services. The method of such interaction is primarily using identifiers associated with digital objects, including
those that represent operations, types, and clients. In this context, a DOIP service, such as a running Cordra instance,
itself is considered a digital object. DOIP is an appropriate choice for users who are interested in an architectural
style focused on invoking identified operations, or who focus on persistence or interoperability benefits.

In DOIP, each request represents a *client* invoking an *operation* on a *target object*.  The client, operation,
and target each have a unique persistent identifier.

This document does not describe further how the DOIP protocol works or the expected structure of digital objects, for
which refer to the DOIP v2 specification. Instead this document describes how to invoke DOIP operations on
Cordra-managed digital objects using examples.

The DOIP v2 implementation in Cordra is a recent addition and has been tested internally.

Configuration
-------------

DOIP interface can be enabled via configuration in the :ref:`design-object`.
(Note: configuration via ``config.json``, which was used for Cordra beta releases, will continue to work but will
be overridden by configuration in the Design object.)  By default, a new Cordra instance will have a DOIP listener
on port 9000, on the same listen address as the HTTP/HTTPS ports.  This can be turned off or changed by editing the Design object.

Per DOIP, the DOIP service itself is sometimes the appropriate target for an operation. By default, the identifier for the DOIP
service is the handle obtained by using the configured prefix from handleMintingConfig (see :ref:`handle-integration`) together
with the suffix "service".  A different serviceId can be configured as follows::

  {
    "doip": {
      "enabled": true,
      "port": 9000,
      "processorConfig": {
        "serviceId": "20.500.123/service"
      }
    }
  }

This configuration should suffice for most uses. Information about other configuration options follows.

listenAddress
~~~~~~~~~~~~~

The address which the DOIP interface should bind to. Defaults to the same as the configured listenAddress for HTTP
interfaces, which defaults to localhost.

port
~~~~

The TCP port which the DOIP interface should bind to.

backlog
~~~~~~~

The backlog of incoming connections for the listening socket. Defaults to 50.

maxIdleTimeMillis
~~~~~~~~~~~~~~~~~

The maximum time for the server to wait for bytes from a client connection. Defaults to 5 minutes.

numThreads
~~~~~~~~~~

The maximum number of server threads for handling DOIP connections. In this experimental setting, defaults to 20.

tlsConfig.id
~~~~~~~~~~~~

The identity to use for the DOIP TLS certificate. Defaults to the same as processorConfig.serviceId. The TLS keys
can be configured via files in the Cordra data directory, named ``doipPrivateKey`` and ``doipPublicKey``
(in Handle format).  These files will be automatically generated on Cordra startup if not present.

..
  tlsConfig.publicKey
  ~~~~~~~~~~~~~~~~~~~
..
  The public key used for the DOIP TLS certificate.
..
  tlsConfig.privateKey
  ~~~~~~~~~~~~~~~~~~~~
..
  The corresponding private key.

processorConfig.serviceId
~~~~~~~~~~~~~~~~~~~~~~~~~

The identifier used for the target of operations intended for the DOIP service itself.
Defaults to the configured handleMintingConfig.prefix together with the suffix "service".

processorConfig.address
~~~~~~~~~~~~~~~~~~~~~~~

The public address to advertise to clients via the 0.DOIP/Op.Hello operation. Defaults to the listenAddress.

processorConfig.port
~~~~~~~~~~~~~~~~~~~~

The public port to advertise to clients via the 0.DOIP/Op.Hello operation. Defaults to the configured port.

processorConfig.publicKey
~~~~~~~~~~~~~~~~~~~~~~~~~

The public key of the DOIP server to advertise to clients via the 0.DOIP/Op.Hello operation. In JWK format. Defaults to
the public key used in the certificates.

..
  processorConfig.baseUri, processorConfig.username, processorConfig.password
  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
..
  This defines a Cordra interface and a username/password to communicate with that interface.  This will be used by the
  DOIP interface to perform Cordra operations.  In general, this should not be needed.


Cordra Objects serialized for DOIP
----------------------------------

A Cordra object has various components:

* id
* type
* content
* acl
* metadata
* payloads

The id and type map directly onto the corresponding properties of a Digital Object as defined in DOIP specification.
The content, acl, and metadata properties of a Cordra object become *attributes* and the payloads become *elements*
of the Digital Object.


DOIP Java Client
----------------

The doip-sdk-2.1.0.jar file, together with its dependencies gson-2.8.5.jar and slf4j-api-1.7.28.jar, can be used to
instantiate a Java DOIP client. You may refer to :ref:`doip_java_client_library` for details on how to access the DOIP
client library in Java. The jar files are also included in the ``sw/lib`` directory of the Cordra distribution.

Instantiate ``net.cnri.doip.client.DoipClient`` and call any of its ``connect`` methods to create a
``net.cnri.doip.client.DoipConnection`` with a DOIP service. The methods of the ``DoipConnection`` can then be used to
send DOIP requests and receive DOIP responses.


DOIP Examples
-------------

A DOIP service listener uses TLS, but many DOIP requests can be entered as plain text within a TLS session. To
experiment with this on the command line, you can use for example::

  openssl s_client -connect localhost:9000

Once connected, you can send requests and receive responses.

To briefly recap the DOIP specification, a DOIP message is a sequence of "segments" separated by ``newline # newline``,
and terminated by ``newline # newline # newline``. JSON segments contain JSON directly; bytes segments begin with
``@ newline``, followed by multiple "chunks", each of which is a decimal number indicating a number of bytes, followed
by that many bytes, followed by a newline. But many DOIP requests can be sent as JSON followed by two lines each with a
``#`` character.  Some examples follow.

Hello
~~~~~

Request::

  {
    "targetId": "20.500.123/service",
    "operationId": "0.DOIP/Op.Hello"
  }
  #
  #

Response::

  {
    "status":"0.DOIP/Status.1",
    "output":{
            "id":"20.500.123/service",
            "type":"0.TYPE/DOIPService",
            "attributes":{
                "ipAddress":"127.0.0.1",
                "port":9000,
                "protocol":"TCP",
                "protocolVersion":"2.0",
                "publicKey":{
                    "kty":"RSA",
                    "n":"m2MIsyH7F7NMA9EABMfPjzbid3MIh9vTP28MKVKFN2waUnPlsb_JM9OfE0cwyRUXuehuNUm7CbaQmOINOFsQhoQBGyj12TnC_Lm__Rgf7Shvl0xKFr83YTa7Zw7HWqOMb_4kY2O7OdV98RIc6oD62cY7j1E_fiudzOnFh5SaXvP3qS3OrNrOA4gODQdplhNikwP5_VwCA45lDnfVBO2Dj62oFl55-BeIc1YQoJ_kkN-8JbNsd3kGKZnq7VDSrGfLAyLLyML9dE7jRK3qxR5Ok_va49KGvQV-krssyacBAIVk1zBUQ8lFnxBcH6g_0Hl_h_zcv-jtfeCCCoZ4sB46Hw==",
                    "e":"AQAB"
                }
            }
    }
  }
  #
  #

Search
~~~~~~

Request::

  {
    "targetId": "20.500.123/service",
    "operationId": "0.DOIP/Op.Search",
    "attributes": {
      "query": "+type:Schema +/name:User"
    }
  }
  #
  #

Response::

  {"status":"0.DOIP/Status.1"}
  #
  {
    "size": 1,
    "results": [
      {
        "id": "test/ccf24d69f39aafee2195",
        "type": "Schema",
        "attributes": {
          "content": {
            "identifier": "test/ccf24d69f39aafee2195",
            "name": "User",
            "schema": {
               ...
            }
          }
        }
      }
    ]
  }
  #
  #

Create
~~~~~~

In general a creation request must specify the "type" and an "attribute" called "content". If you wish to specify the id
of the newly created object, specify an "id" as a sibling property of "type" and "attributes".

Request::

  {
    "targetId": "20.500.123/service",
    "operationId": "0.DOIP/Op.Create",
    "input": {
      "type": "User",
      "attributes": {
        "content": {
          "username": "user",
          "password": "password"
        }
      }
    },
    "authentication": {
      "username": "admin",
      "password": "password"
    }
  }
  #
  #

Response::

  {
    "status":"0.DOIP/Status.1",
    "output":{
        "id":"test/12dea96fec20593566ab",
        "type":"User",
        "attributes":{
            "content":{
                "id":"test/12dea96fec20593566ab",
                "username":"user",
                "password":""
            },
            "metadata":{
                "createdOn":1537467895407,
                "createdBy":"admin",
                "modifiedOn":1537467895450,
                "modifiedBy":"admin",
                "txnId":6
            }
        },
        "elements":[]
    }
  }
  #
  #

Request::

  {
    "clientId": "test/12dea96fec20593566ab",
    "targetId": "20.500.123/service",
    "operationId": "0.DOIP/Op.Create",
    "authentication": {
      "password": "password"
    }
  }
  #
  {
    "type": "Document",
    "attributes": {
      "content": {
        "id": "",
        "name": "Hello World"
      }
    },
    "elements": [
      {
        "id": "file",
        "type": "text/plain",
        "attributes": {
          "filename": "helloworld.txt"
        }
      }
    ]
  }
  #
  {"id":"file"}
  #
  @
  12
  Hello World

  #
  #

Response::

  {
    "status":"0.DOIP/Status.1",
    "output":{
        "id":"test/0a4d55a8d778e5022fab",
        "type":"Document",
        "attributes":{
            "content":{
                "id":"test/0a4d55a8d778e5022fab",
                "name":"Hello World"
            },
            "metadata":{
                "createdOn":1537469656224,
                "createdBy":"test/12dea96fec20593566ab",
                "modifiedOn":1537469656235,
                "modifiedBy":"test/12dea96fec20593566ab",
                "txnId":7
            }
        },
        "elements":[
            {
                "id":"file",
                "length":0,
                "type":"text/plain",
                "attributes":{
                    "filename":"helloworld.txt"
                }
            }
        ]
    }
  }
  #
  #

Retrieve
~~~~~~~~

Request::

  {
    "targetId": "test/0a4d55a8d778e5022fab",
    "operationId": "0.DOIP/Op.Retrieve"
  }
  #
  #

Response::

  {
    "status":"0.DOIP/Status.1",
    "output":{
        "id":"test/0a4d55a8d778e5022fab",
        "type":"Document",
        "attributes":{
            "content":{
                "id":"test/0a4d55a8d778e5022fab",
                "name":"Hello World"
            },
            "metadata":{
                "createdOn":1537469656224,
                "createdBy":"test/12dea96fec20593566ab",
                "modifiedOn":1537469656235,
                "modifiedBy":"test/12dea96fec20593566ab",
                "txnId":7
            }
        },
        "elements":[
            {
                "id":"file",
                "length":0,
                "type":"text/plain",
                "attributes":{
                    "filename":"helloworld.txt"
                }
            }
        ]
    }
  }
  #
  #

Request::

  {
    "targetId": "test/0a4d55a8d778e5022fab",
    "operationId": "0.DOIP/Op.Retrieve",
    "attributes": {
      "element": "file"
    }
  }
  #
  #

Response::

  {"status":"0.DOIP/Status.1"}
  #
  @
  12
  Hello World

  #
  #

Extended Operations
~~~~~~~~~~~~~~~~~~~

Cordra enables developers to extend the core functionality and expose that as extended operations. See :ref:`type-methods`
for how to write extended operations.

In the case of functionality that is added as an instance Type method, the targetId of the DOIP operation will be digital
object identifier to which this instance Type method should be applied. In the case of any static Type method, the targetId
will be the digital object identifier of the Type. In either case, the operationId is the identifier that is specified in
the JavaScript export statement associated with the method definition.

Below you will find the DOIP request and response structures that applies to the static Type method defined
:ref:`here <static_method_example>`.

Request::

  {
    "targetId": "test/7060f82cc15962ba4851",
    "operationId": "123/abc",
    "authentication": { "username": "admin", "password": "password" },
    "input": {"foo":"hello", "bar":"world"}
  }
  #
  #

Here test/7060f82cc15962ba4851 is the identifier of the Type digital object on which the static Type method is defined.

Response::

  {
    "status":"0.DOIP/Status.001",
    "output":{"input":{"foo":"hello","bar":"world"},"timestamp":1568752848904}
  }
  #
  #

