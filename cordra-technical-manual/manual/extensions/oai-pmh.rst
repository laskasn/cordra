.. _oai_pmh:

OAI-PMH in Cordra
=================
We created a proof-of-concept to demonstrate how digital objects in Cordra can be disseminated via OAI-PMH. This is not a generic
application that automatically associates an OAI-PMH interface with any Cordra instance, but a specific demonstration that
we had put together that could act as a template or a starting point for Cordra projects with OAI-PMH needs.

For this proof-of-concept, we make use of sample Paper and Book types, and enable OAI-PMH access to objects of those
types. Paper type is a stand-in for an academic research paper, and Book type is a stand-in for published books.
Here are a few examples of OAI-PMH calls that will work in your local Cordra instance once the proof-of-concept is enabled:

Example OAI-PMH URLs
--------------------
Identify
~~~~~~~~
::

    http://localhost:8080/cordra-oai-pmh/?verb=Identify

ListIdentifiers
~~~~~~~~~~~~~~~
::

    http://localhost:8080/cordra-oai-pmh/?verb=ListIdentifiers&from=2000-01-01T00:00:00Z&metadataPrefix=oai_dc

GetRecord
~~~~~~~~~
::

    http://localhost:8080/cordra-oai-pmh/?verb=GetRecord&identifier=test/eb91eb599520927f9c8e&metadataPrefix=oai_dc

Here ``test/eb91eb599520927f9c8e`` is the id of a sample Cordra object. Replace it with an actual identifier if you are
not using the sample data.

ListRecords
~~~~~~~~~~~
::

    http://localhost:8080/cordra-oai-pmh/?verb=ListRecords&from=2000-01-01T00:00:00Z&metadataPrefix=oai_dc

    http://localhost:8080/cordra-oai-pmh/?verb=ListRecords&resumptionToken=token_returned_from_a_previous_call

ListMetadataFormats
~~~~~~~~~~~~~~~~~~~
::

    http://localhost:8080/cordra-oai-pmh/?verb=ListMetadataFormats

Design
------
Enabling OAI-PMH support within Cordra using our application requires two sets of capabilities:

* Metadata Translation: A capability that instructs the OAI-PMH interface how to convert Cordra objects into OAI-PMH
  expected metadata. We enabled this capability as a set of :ref:`type-methods` that are configured on
  each type of Cordra object meant for OAI-PMH based dissemination. These methods, at runtime, read the information
  from a given Cordra object and produce bytes that are meaningful from a OAI-PMH dissemination standpoint. Details
  about these specific methods are discussed in the following sections.

* Protocol Interface: A capability that accepts OAI-PMH requests from users and invokes the aforementioned methods to
  produce the desired responses. We enabled this as a Java servlet application that should be appropriately
  configured to reach the Cordra it is fronting.

Setup
-----

The sample objects and servlet application required are included in the Cordra download, in the ``extensions/oai-pmh``
directory.

Servlet Install
~~~~~~~~~~~~~~~

The Java servlet application war file should be placed in the ``data/webapps/`` directory of the Cordra deployment.
The data directory of the Cordra deployment should also contain a configuration file called ``oai-pmh-config.json``.

Example configuration file::

    {
        "identity" : {
            "adminEmail" : "admin@example.com",
            "baseURL" : "http://localhost:8080/cordra-oai-pmh/",
            "repositoryName" : "Test"
        },
        "maxPageSize" : 2,
        "cordraBaseUri" : "http://localhost:8080/",
        "cordraTypes" : ["Book", "Paper"]
    }

The ``identity`` information in the configuration file is used by the servlet application to respond to Identify
verb requests.

The ``maxPageSize`` property is used by the servlet application when list requests, i.e., ``ListRecords`` and
``ListIdentifiers``, are made. In the above example, it has been set to a very small number to demonstrate request
resumption tokens in a system that only contains a handful of objects. In a production deployment this number should
be set to something much larger.

``cordraBaseUri`` is the URI to which the Java servlet application connects to interact with Cordra. Since the Java
servlet application is run on the same environment as Cordra in our proof of concept, the above example refers to the
Cordra instance deployed at the localhost.

``cordraTypes`` lists the types of objects in the Cordra instance that should be made available to the OAI-PMH
interface. For example, you might have objects of type Group or User that you do not want to expose over this interface.
In the example deployment, two different types are used, Paper which represents the metadata of an academic research
paper, and Book which represents the metadata for a published book.

Type Methods
~~~~~~~~~~~~

The zip file download includes the necessary type object for our example application, in addition to a few sample
objects. There is also a type called ``OaiUtil`` that contains some Javascript methods that are used by the Book
and Paper types.

You can load the objects using the Cordra UI. Sign in into Cordra as ``admin`` and select the ``Admin->Types``
dropdown menu. Click the "Load from file" button. In the dialog that pops up, select the ``oai-pmh-objects.json`` file
you downloaded and check the box to delete existing objects. Click "Load" to import the objects into Cordra.

To see examples of the methods described below, sign in as admin to the Cordra UI look at the Book and Paper types.

For details about how to write methods like these in Cordra, please refer to :ref:`type-methods`.

Authorization
~~~~~~~~~~~~~

The type methods should be configured such that they can be publicly accessed (so that the servlet
application can access them without any specific credentials). To do that, in the ``Admin->Authorization`` section of
the Cordra UI, replace the default Authorization config with the following::

    {
      "schemaAcls": {
        "Book": {
          "defaultAclRead": [
            "public"
          ],
          "defaultAclWrite": [
            "public"
          ],
          "aclCreate": [],
          "aclMethods": {
            "default": {
              "instance": [
                "public"
              ],
              "static": [
                "public"
              ]
            }
          }
        },
        "Paper": {
          "defaultAclRead": [
            "public"
          ],
          "defaultAclWrite": [
            "public"
          ],
          "aclCreate": [],
          "aclMethods": {
            "default": {
              "instance": [
                "public"
              ],
              "static": [
                "public"
              ]
            }
          }
        },
        "CordraDesign": {
          "defaultAclRead": [
            "public"
          ],
          "defaultAclWrite": [],
          "aclCreate": []
        },
        "Schema": {
          "defaultAclRead": [
            "public"
          ],
          "defaultAclWrite": [],
          "aclCreate": []
        }
      },
      "defaultAcls": {
        "defaultAclRead": [
          "public"
        ],
        "defaultAclWrite": [
          "creator"
        ],
        "aclCreate": [
          "authenticated"
        ]
      }
    }


This configuration enables public access to all instance and static methods on Book and Paper types.

Metadata Translation using JavaScript
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The proof of concept enables five OAI-PMH request verbs: Identify, ListIdentifiers, GetRecord, ListRecords, and
ListMetadataFormats.

* Identify: The information necessary to respond to the Identify verb is already configured with the servlet application
  (see next section for details). As such, no specific support for this verb at Cordra is needed.

* ListIdentifiers: Cordra’s REST API already supports listing identifiers of Cordra objects of a given type. As such,
  no specific Type method is required to support this verb.

* GetRecord: OAI-PMH expects a metadata description for each object. At the minimum for such description, OAI-PMH expects
  a DC format, which is short for XML serialization of metadata that uses properties from Dublin Core specification.
  OAI-PMH allows metadata descriptions in other formats. Because information in Cordra objects need not be readily
  available for dissemination per OAI-PMH, Type methods are required to translate Cordra objects to meet OAI-PMH needs.
  One method getAsXml should be associated with each type of Cordra object. That method should have instructions to
  convert information from the Cordra object into the desired XML. If the intention is to just produce XML that
  conforms to DC format, a convenience method ``getAsDublinCoreJson`` can be used instead. This method expects a
  JSON object containing various DC fields. The OAI-PMH servlet application is hard-wired to execute either of these
  methods depending on the user request. Example code for these two methods is listed in the Appendix.

* ListRecords: ListRecords is, in effect, a listing of all records where each record corresponds to the GetRecord
  response for a given object. As such, the methods discussed above are sufficient for the servlet application to
  fulfill user requests that correspond to this verb.

* ListMetadataFormats: As mentioned above, metadata formats other than DC can be supported by an OAI-PMH interface.
  Because Cordra objects are typed, each type should indicate the supported metadata formats. A method
  ``listMetadataFormats`` should be used to return a list of supported formats. Example code is listed in the Appendix.

Protocol Interface Servlet Application
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A Java servlet application was developed for this proof of concept that performs three actions:

* It accepts valid OAI-PMH requests from users,
* It invokes appropriate Cordra APIs (and in turn invokes appropriate Type methods wherever applicable) to
  retrieve information for a given user request, and
* It serializes the retrieved information to the desired form and responds back to the users.

Resumption Token
~~~~~~~~~~~~~~~~
Resumption token, according to OAI-PMH, is a token that enables clients to retrieve the next set of results for a
given request when the total number of results are deemed large enough to require multiple responses. The resumption
token is implemented as a stateless string: a base64 encoding of the bytes of a JSON string that when
decoded informs Cordra the next set of Cordra objects that should be returned. For example, the decoded information may
look like this::

    {"txnId":1557422842749010,"from":"2000-01-01T00:00:00Z","metadataPrefix":"oai_dc"}

Appendix
--------
Metadata for the OAI-PMH results are generated at runtime using Type methods. In particular, the Java servlet
application knows to invoke specific methods that are attached to the applicable types. Those methods are ``getAsXml``,
``getAsDublinCoreJson``, and ``listMetadataFormats``.

getAsXml
~~~~~~~~
This method expects two arguments: the instance of the Cordra object and a context object. In this case, the context
object will contain a string property context.params.format. The value of this property will contain the metadataPrefix
of the originating OAI-PMH call. Your code should branch on the value of this property and construct an XML string in
the requested format. The function should return that XML string. If you do not want this type of object to support the
requested format you should throw an exception like this:

.. code-block:: js

    var format = context.params.format;
    if ("oai_custom_1" !== format) throw "Format not supported";

If your object does support the requested format, construct an XML string and return it. An example is shown below:

.. code-block:: js

    var paper = cordraObject.content;
    var xml = '<paper>';
    xml += '<identifier>'+ cordraObject.id +'</identifier>';
    if (paper.title) {
        xml += '<title>'+paper.title+'</title>';
    }
    if (paper.abstract) {
        xml += '<abstract>'+paper.abstract+'</abstract>';
    }
    xml += '</paper>';
    return xml;

In the example on the Book type, an included utility function ``oaiUtil.toXml()`` is used to generate the XML from the
JSON of the object automatically.

.. code-block:: js

    var book = cordraObject.content;
    var xml = oaiUtil.toXml("book", book);

Note that ``oaiUtil.toXml()`` is implemented for this proof-of-concept; as implemented, this method ``toXml()`` does not
support attributes, and implements one specific way of representing lists of elements. It is included here solely to
provide a starting point, one way in which a developer might choose to generate their specific XML records.

getAsDublinCoreJson
~~~~~~~~~~~~~~~~~~~
The XML serialization of Dublin Core metadata is a known structure to the servlet application. As such a utility is
provided that can automatically convert a specific JSON object with correctly named properties into a Dublin Core XML
record. The return value of the method getAsDublinCoreJson should be a JSON object with property names that match the
15 terms from the Dublin Core set:

.. code-block:: js

    ["title", "creator", "subject", "description", "publisher", "contributor", "date", "type", "format", "identifier", "source", "language", "relation", "coverage", "rights"]

You can either include a single top level property, or if you want to include more than one property of that type, you
can use an array with the same name like in this example:

.. code-block:: js

    var dcJson = {
        "title" : "foo",
        "creator" : ["Bob", "Alice"]
    };

The Java servlet application (cordra-oai-pmh.war) will automatically convert the returned JSON object into Dublin Core compatible
XML including the ``dc:`` prefix to the property names.

An additional utility method ``oaiUtil.mapTo()`` can be used to map the values on the input Cordra object to the desired
output properties. It takes a map from output property name to path of input property, like this:

.. code-block:: js

    var map = {
        "subject" : "/genre",
        "creator" : "/authors",
        "publisher" : "/publishedBy",
        "language" : "/language",
        "date" : "/publicationDate"
    };
    var dcJson = oaiUtil.mapTo(cordraObject, map);

listMetadataFormats
~~~~~~~~~~~~~~~~~~~
For each type of digital object that you are using with OAI-PMH, the metadata formats must be explicitly listed. This is done by
implementing and exporting the following JavaScript method, like this:

.. code-block:: js

    exports.staticMethods = {};
    exports.staticMethods.listMetadataFormats = listMetadataFormats;

    function listMetadataFormats() {
        return [
            {
                "metadataPrefix" : "oai_dc",
                "schema" : "http://www.openarchives.org/OAI/2.0/oai_dc.xsd",
                "metadataNamespace" : "http://www.openarchives.org/OAI/2.0/oai_dc/"
            },
            {
                "metadataPrefix" : "oai_custom_1"
            }
        ];
    }


