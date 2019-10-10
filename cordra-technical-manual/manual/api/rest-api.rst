.. _rest_api:

HTTP REST API and Examples
==========================

Cordra provides a RESTful HTTP API for interacting with digital objects.
Cordra also provides a web browser based interface that dynamically creates object
editors based on configured :ref:`types`, mainly for administrative purposes.

Cordra HTTP requests that conform to HTTP/1.1 and HTTP/2 specifications (RFCs 7230, 7231, and 7540) are tested.

Overview
--------

Main APIs

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

=====================================================================================   ====================
Resource                                                                                Description
=====================================================================================   ====================
`GET /objects/<id> <#retrieve-object-by-id>`_                                           Retrieves an object or part of an
                                                                                        object by id.

`POST /objects/?type=<type> <#create-object-by-type>`_                                  Create an object by type.

`PUT /objects/<id> <#update-object-by-id>`_                                             Update an object by id.

`GET /objects/?query=<query> <#search-for-objects>`_                                    Search for objects.

`DELETE /objects/<id> <#delete-object-by-id>`_                                          Delete an object by id.

`DELETE /objects/<id>?payload=<payload> <#delete-payload-by-id-and-name>`_              Delete a payload by id and name.

`GET http://hdl.handle.net/<id> <#retrieve-an-object-via-the-handle-net-web-proxy>`_    Retrieves an object via the
                                                                                        Handle proxy.

`GET /acls/<id> <#retrieve-acl-for-object>`_                                            Retrieve the ACLs for a specific
                                                                                        object.

`PUT /acls/<id> <#update-acl-for-object>`_                                              Modify the ACLs for a specific
                                                                                        object.

`POST /users/this/password <#change-password>`_                                         Change the password of the
                                                                                        currently authenticated user.

`GET /check-credentials <#check-credentials>`_                                          Retrieve information
                                                                                        about provided credentials.

`POST /call <#call-type-method>`_                                                       Call a Type method.

`GET /listMethods <#list-type-methods>`_                                                List Type methods.
=====================================================================================   ====================

Access Token APIs

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

=====================================================================================   ====================
Resource                                                                                Description
=====================================================================================   ====================
`POST /auth/token <#create-a-new-access-token>`_                                        Create a new access token.

`POST /auth/introspect <#get-access-token-information>`_                                Retrieve information
                                                                                        about the specified access token.

`POST /auth/revoke <#delete-specified-access-token>`_                                   Delete specified access token (used to "sign out").
=====================================================================================   ====================

Administrative APIs

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

====================================================   ====================
Resource                                               Description
===========================================================================
`GET /schemas/<type> <#retrieve-schema>`_              Get schema or all schemas.

`PUT /schemas/<type> <#create-or-update-schema>`_      Create or update a schema.

`DELETE /schemas/<type> <#delete-schema>`_             Delete a schema.

`POST /uploadObjects <#upload-objects>`_               Bulk upload of objects from
                                                       a json file.

`PUT /adminPassword <#update-admin-password>`_         Change the password for the
                                                       admin user.

`POST /updateHandles <#start-handle-update>`_          Update all handle records for
                                                       objects in Cordra.

`GET /updateHandles <#get-status-of-handle-update>`_   Get status of handle update.

`POST /reindexBatch <#reindex-batch-of-objects>`_      Reindex all specified objects.

`GET /startupStatus <#startup-status>`_                Accessible even after failed
                                                       startup to indicate success
                                                       or failure
====================================================   ====================

Experimental APIs

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

=================================================================   ====================
Resource                                                            Description
=================================================================   ====================
`GET /versions/?objectId=<objectId> <#retrieve-object-version>`_    Get version information for
                                                                    a specific object.

`POST /versions/?objectId=<objectId> <#create-object-version>`_     Create a version of a specific
                                                                    object.
=================================================================   ====================


HTTP Response Codes
-------------------

=========================   ====================
HTTP Response Code          Description
=========================   ====================
200 OK                      The request was successfully processed.

400 Bad Request             There was an error with the request, such as a creation or update which is not schema-valid.

401 Unauthorized            Actually "Unauthenticated".  There is no authenticated user, or authentication failed,
                            but the requested operation requires authentication.

403 Forbidden               The authenticated user does not have permissions to perform the requested operation.

404 Not Found               The Cordra object or requested part does not exist.

409 Conflict                An attempt was made to create a Cordra object with a handle already in use by a Cordra object.

500 Internal Server Error   An unexpected server error occurred.  Usually diagnosis requires looking at the server logs.
=========================   ====================

Range requests for payloads can additionally return 206 Partial Content and 416 Range Not Satisfiable,
following the standard specification for HTTP range requests.

Authentication
--------------

For any Cordra API call that requires authentication, you can either authenticate directly for the single call,
or provide an access token. Access tokens are used to maintain a server session in between requests. This way,
the server does not need to reprocess your authentication credentials with each request. Using sessions can
significantly speed up the process of making multiple requests.

There are two ways to authenticate directly. One is to include username and password following the HTTP Basic Authentication
method. Another is to authenticate using public-private key pair following the HTTP Bearer Authentication method with a JWT.
For more information on the topic, see :ref:`aa`.

To obtain an access token, you must authenticate using the :ref:`tokenApi`. Once authenticated, you will receive an
access token that should be sent back with subsequent calls. This token should be sent using an Authorization Bearer
header. For example::

    Authorization: Bearer ACCESS_TOKEN


.. _requestContext:

Request Context
---------------

All requests allow a query parameter called ``requestContext``.  This must be valid JSON object (suitably encoded
as a query parameter).  The requestContext is made available to JavaScript methods and lifecycle hooks as part of the
``context`` argument (see :ref:`javascript-lifecycle-hooks`).  It is also made available to the StorageChooser used
with :ref:`multiStorage`.


API Examples
------------

In the following examples the schema shown below was added to the server
as type ``Document``. Multiple types can be added. The server will only
accept POST and PUT requests for objects that conform to the schema
corresponding to the object type; other requests will receive a 400 Bad
Request response.

Example Schema::

    {
        "type": "object",
        "title": "Document",
        "required": [
            "name",
            "description"
        ],
        "properties": {
            "id": {
                "type": "string",
                "cordra": {
                    "type": {
                        "autoGeneratedField": "handle"
                    }
                }
            },
            "name": {
                "type": "string",
                "maxLength": 128,
                "title": "Name"
            },
            "description": {
                "type": "string",
                "title": "Description"
            },
            "creator": {
                "type": "object",
                "title": "Creator",
                "properties": {
                    "fullName": {
                        "type": "string",
                        "title": "Full Name"
                    },
                    "organization": {
                        "type": "string",
                        "title": "Organization"
                    }
                }
            }
        }
    }


Objects API
~~~~~~~~~~~

Retrieve object by id
#####################

Request::

    GET /objects/<id>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
id                      required                The id of the desired
                                                object.

jsonPointer             optional                The jsonPointer into
                                                the subcomponent of
                                                the target object

filter                  optional                A Json array of
                                                jsonPointers used to
                                                restrict the result
                                                object.

payload                 optional                The name of the
                                                payload to retrieve

pretty                  optional                Format returned json

text                    optional                When present on a
                                                request which would
                                                normally result in a
                                                JSON string, the
                                                response is the
                                                contents of the JSON
                                                string

disposition             optional                For payload requests.
                                                Can be used to set
                                                the
                                                Content-Disposition
                                                header on the
                                                response;
                                                "disposition=attachment"
                                                will cause a standard
                                                web browser to
                                                perform a download
                                                operation

full                    optional                If present the
                                                response is the full
                                                Cordra object,
                                                including properties
                                                id, type, content,
                                                acl, metadata, and
                                                payloads.  By default
                                                only the content is
                                                returned.
=====================   =====================   ====================

|

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Request headers
====================================================================
Range                   optional                If present in a
                                                payload request, only
                                                retrieve the
                                                requested bytes from
                                                the payload.
=====================   =====================   ====================

|

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=================================   ====================
Response headers
========================================================
X-Schema                            Type of the object

X-Permission                        What the calling user is
                                    authorized to do with object.
                                    READ or WRITE. Any caller with
                                    WRITE permission is also
                                    permitted to read the object.
=================================   ====================

Examples
########

Request::

    GET /objects/20.5000.1234/1321d2d033b22bee1187

Response::

    {
        "id" : "20.5000.1234/1321d2d033b22bee1187",
        "name" : "A file",
        "description" : "It's a file",
        "creator" : {
            "fullName" : "John Doe",
            "organization" : "Acme Corp."
        }
    }

Request::

    GET /objects/20.5000.1234/1321d2d033b22bee1187?jsonPointer=/creator

Response::

    {
        "fullName" : "John Doe",
        "organization" : "Acme Corp."
    }

Request::

    GET /objects/20.5000.1234/1321d2d033b22bee1187?jsonPointer=/description&text

Response::

    It's a file

Request::

    GET /objects/20.5000.1234/1321d2d033b22bee1187?payload=file

Response::

    (Contents of the payload)

Create object by type
#####################

Request::

    POST /objects/?type=Document

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
type                    required                The type of the
                                                object being created.
                                                In this case
                                                "Document".

dryRun                  optional                Do not actually
                                                create the object.
                                                Will return results
                                                as if object had
                                                been created.

suffix                  optional                The suffix of the
                                                handle used to
                                                identify this object.
                                                One will be generated
                                                if neither 'suffix'
                                                nor 'handle' is
                                                specified.

handle                  optional                The handle used to
                                                identify this object.
                                                One will be generated
                                                if neither 'suffix'
                                                nor 'handle' is
                                                specified.

full                    optional                If present the
                                                response is the full
                                                Cordra object,
                                                including properties
                                                id, type, content,
                                                acl, metadata, and
                                                payloads.  By default
                                                only the content is
                                                returned.
=====================   =====================   ====================

See :ref:`handle-minting-configuration`  for configuring the handle
prefix for automatic handle generation when the "handle" parameter is not used.

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

============   ====================
Request Headers
===================================
Content-Type   application/json OR multipart/form-data
============   ====================

|

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=================================   ====================
Response Headers
========================================================
Location                            URI for accessing the created
                                    object; includes the id of the
                                    created object
=================================   ====================

Examples
########

POST Data::

    {
        "id" : "",
        "name" : "A different file",
        "description" : "This one doesn't contain a file",
        "creator" : {
            "fullName" : "Jane Doe",
            "organization" : "Acme Labs."
        }
    }

Response::

    {
        "id" : "20.5000.1234/23bdf2a62a83225a1b77",
        "name" : "A different file",
        "description" : "This one doesn't contain a file",
        "creator" : {
            "fullName" : "Jane Doe",
            "organization" : "Acme Labs"
        }
    }

To create an object with one or more payloads, POST data of type
multipart/form-data must be sent. There must be one part named ``content``
which is the JSON content of the object to be created.
There may optionally be a part named ``acl`` which will be the acl component
of the new object; it must be a JSON object with two properties ``"readers"``
and ``"writers"``, each a JSON array of strings.

Parts which have have filenames determine payloads.  The payload name is
the part name.  The filename and a Content-Type if present are
stored as the metadata of the payload.

POST Data::

    --PART-SEPARATOR
    Content-Disposition: form-data; name="content"

    {
        "id": "",
        "name": "Really a file",
        "description": "Really a file",
        "file": ""
    }
    --PART-SEPARATOR
    Content-Disposition: form-data; name="file"; filename="a.html"
    Content-Type: text/html

    ...
    --PART-SEPARATOR--

Update object by id
###################

Request::

    PUT /objects/<id>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
id                      required                The id of the object
                                                to update.

dryRun                  optional                Do not actually
                                                update the object.
                                                Will return results
                                                as if object had
                                                been updated.

type                    optional                If specified,
                                                indicates a request
                                                to change the type of
                                                the object.

payloadToDelete         optional                The name of an
                                                existing payload to
                                                delete. Can be used
                                                multiple times.

jsonPointer             optional                A JSON pointer within
                                                the object's content.
                                                Only the JSON at that
                                                JSON pointer will be
                                                updated.

full                    optional                If present the
                                                response is the full
                                                Cordra object,
                                                including properties
                                                id, type, content,
                                                acl, metadata, and
                                                payloads.  By default
                                                only the content is
                                                returned.
=====================   =====================   ====================

|

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

============   ====================
Request Headers
===================================
Content-Type   application/json OR multipart/form-data
============   ====================

Examples
########

Request::

    PUT /objects/20.5000.1234/23bdf2a62a83225a1b77

PUT Data::

    {
        "id" : "20.5000.1234/23bdf2a62a83225a1b77",
        "name" : "A different file",
        "description" : "I've changed the description",
        "creator" : {
            "fullName" : "Jane Doe",
            "organization" : "Acme Labs."
        }
    }

Response::

    {
        "id" : "20.5000.1234/23bdf2a62a83225a1b77",
        "name" : "A different file",
        "description" : "I've changed the description",
        "creator" : {
            "fullName" : "Jane Doe",
            "organization" : "Acme Labs."
        }
    }

When updating an object with payloads, existing payloads can be omitted
from the uploaded JSON data. Those payloads will be unchanged. New
payloads and modified payloads should be included as parts in a
multipart/form-data request, as for the object creation API.
Additionally a payload can be deleted by including its names as the
value of a ``payloadToDelete`` parameter. Multiple ``payloadToDelete``
parameters are allowed.

Search for objects
##################

Request::

    GET /objects/?query=<query>

.. tabularcolumns:: |\X{1}{7}|\X{2}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
query                   required                The query to be
                                                processed.

ids                     optional                If specified, the search
                                                returns the ids of the
                                                matched objects only.

pageNum                 optional, default 0     The desired results
                                                page number. 0 is the
                                                first page.

pageSize                optional                The number of results
                                                per page. If omitted
                                                or negative
                                                no limit. If 0 no results
                                                are returned, only the
                                                size (number of hits).

sortFields              optional                Sort fields for the query
                                                results.  The format is
                                                comma-separated, with each
                                                field name optionally
                                                followed by ASC or DESC
                                                to indicate the sort
                                                direction.

full                    optional                If set to false only the
                                                content of the object is
                                                returned.

filter                  optional                A Json array of
                                                jsonPointers used to
                                                restrict the result
                                                object. The jsonPointers
                                                are relative to the root
                                                of the objects in the
                                                results. Note that that
                                                root changes if the full
                                                param is set to false.
=====================   =====================   ====================

Examples
########

Request::

    GET /objects/?query=file&pageNum=0&pageSize=10

Response::

    {
        "size": 1,
        "pageNum": 0,
        "pageSize": 10,
        "results": [
            {
                "id": "20.5000.1234/1321d2d033b22bee1187",
                "type": "Document",
                "content": {
                    "id": "20.5000.1234/1321d2d033b22bee1187",
                    "name": "A file",
                    "description": "Its a file",
                    "file": "",
                    "creator": {
                        "fullName": "John Doe",
                        "organization": "Acme Corp."
                    }
                }
            }
        ]
    }

The query format is that used by the indexing backend, which is
generally the inter-compatible Lucene/Solr/Elasticsearch
format for fielded search.  The fields include the payload name
for payloads, and modified JSON Pointers for components of the
object JSON content, where array indices are replaced with "_"
as a wildcard.  Typical field names are "/id", "/name",
"/creator/organization", "/users/_/id".  The special field
names "id", "type", "aclRead", and "aclWrite" can be used to
search for objects by id, type, and acl.  The special sort
field name "score" can be used to sort by score as determined
by the indexing backend.

Fields under Cordra object "metadata" and "userMetadata" are also
indexed and searchable, using fields which are "metadata" or
"userMetadata" prepended to the JSON pointer
within the metadata or userMetadata object.  Examples include
"metadata/createdOn", "metadata/createdBy", "metadata/modifiedOn",
"metadata/modifiedBy".

See :doc:`search` for more details and example for the search API.

If the boolean parameters "ids" is set, for example as
GET /objects/?query=...&ids, then the "results" will
just be a list of ids rather than a list of Cordra objects.

Note: Former versions of Cordra would return all results with
pageSize=0.  To restore this former behavior, you can add
``"useLegacySearchPageSizeZeroReturnsAll":true`` to the Cordra design
object.  By default a search with pageSize=0 returns the number of
matched objects but no object content.

Request::

    GET /objects/?query=file&filter=["/id","/content/name"]

Response::

    {
        "size": 1,
        "pageNum": 0,
        "pageSize": -1,
        "results": [
            {
                "id": "20.5000.1234/1321d2d033b22bee1187",
                "content": {
                    "name": "A file"
                }
            }
        ]
    }

Here the filter param is used to restrict the properties in the result objects.
This may be desirable if your stored objects are large and your application only
requires a part of each object.

Delete object by id
###################

Request::

    DELETE /objects/<id>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

===========   ========   ====================
Parameters
=============================================
id            required   The id of the object to delete.

jsonPointer   optional   Indicates that instead of
                         deleting the object, the object
                         should be modified by deleting
                         the content at the specified
                         JSON pointer.
===========   ========   ====================

Examples
########

Request::

    DELETE /objects/20.5000.1234/23bdf2a62a83225a1b77

Response: empty

Delete payload by id and name
#############################

Request::

    DELETE /objects/<id>?payload=<payload>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=======   ========   ====================
Parameters
=========================================
id        required   The id of the object containing the payload.

payload   required   The name of the payload to delete.
=======   ========   ====================

Examples
########

Request::

    DELETE /objects/20.5000.1234/23bdf2a62a83225a1b77?payload=file

Response: empty

Handle.Net Web Proxy
~~~~~~~~~~~~~~~~~~~~

Retrieve an object via the Handle.Net web proxy
###############################################

Request::

    GET http://hdl.handle.net/20.5000.1234/23bdf2a62a83225a1b77?locatt=view:json

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
locatt                  optional, ``view:ui``   Used to specify if
                        or ``view:json``.       the redirect should
                        (default ``view:ui``)   respond with the json
                                                or the user
                                                interface.
=====================   =====================   ====================

See :ref:`handle-integration` for details about handle generation.

ACL API
~~~~~~~

Retrieve ACL for object
#######################

Request::

    GET /acls/<id>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

==   ========   ====================
Parameters
====================================
id   required   The id of the object to retrieve its acl.
==   ========   ====================

Example
#######

Request::

    GET /acls/20.5000.1234/37b4ac94ba3e14665e04

Response::

    {
        "readers": [
            "20.5000.1234/73675debcd8a436be48e"
        ],
        "writers": [
            "20.5000.1234/73675debcd8a436be48e"
        ]
    }

Update ACL for object
#####################

Request::

    PUT /acls/<id>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

==   ========   ====================
Parameters
====================================
id   required   The id of the object you want to set permissions on.
==   ========   ====================

Example
#######

Request::

    PUT /acls/20.5000.1234/37b4ac94ba3e14665e04

PUT Data::

    {
        "readers": [
            "20.5000.1234/73675debcd8a436be48e"
        ],
        "writers": [
            "20.5000.1234/73675debcd8a436be48e"
        ]
    }

The PUT data contains two arrays, ``readers`` and ``writers``. These arrays
should contain the ids of the users that are given the associated permission.
Note that if a user is granted write permission this implicitly grants
them read permission.

The standard ``PUT /objects`` update API can be used to modify ACL values as well,
by including a part named "acl" in a multipart request.

**NOTE:** The ACL API has changed in Cordra 2.0. In version 1.0, the data arrays
were called ``read`` and ``write``.

Response::

    {
        "readers": [
            "20.5000.1234/73675debcd8a436be48e"
        ],
        "writers": [
            "20.5000.1234/73675debcd8a436be48e"
        ]
    }

.. _passwordChangeApi:

Password Change API
~~~~~~~~~~~~~~~~~~~

Change password
###############

Request::

    PUT /users/this/password

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=============   ====================
Request Headers
====================================
Authorization   Should be a Basic auth header or Bearer auth header with a JWT
=============   ====================

Changing a password requires using the Authorization header directly authenticating the user, 
instead of an access token. The body of the request should just be the new password.

Example
#######

Request::

    PUT /users/this/password

PUT Data::

    newPassword

Response::

    {
        "success": true
    }


Check Credentials API
~~~~~~~~~~~~~~~~~~~~~

Check Credentials
#################

Request::

    POST /check-credentials

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=============   ====================
Request Headers
====================================
Authorization   Should be a Basic or Bearer auth header. Optional.
=============   ====================

This call can be used to get information about the provided credentials. It can be used to check credentials for
the given Authorization header, either for a direct authentication or for an access token.
(It can also be used with the the :ref:`legacySessionsApi` to check credentials of a cookie-based session.)

Example
#######

Request::

    GET /check-credentials

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
full                    optional                If ``?full=true`` is specified as a query parameter,
                                                additional fields are included in the response.
=====================   =====================   ====================

.. tabularcolumns:: |\X{3}{7}|\X{4}{7}|

========================   ====================
Response Attribute Name    Description
===============================================
active                     Whether or not the authentication was successful.

username                   Username of the authenticated user

userId                     UserId of the authenticated user

typesPermittedToCreate     List of types this user can create; included when ``?full=true`` is specified.

groupIds                   List of groups this user is in; included when ``?full=true`` is specified.
========================   ====================

Type Methods API
~~~~~~~~~~~~~~~~

See :doc:`../design/type-methods` for more details.

Call Type method
################

Request::

    POST /call/?objectId=<objectId>&method=<method>
    POST /call/?type=<type>&method=<method>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

========   ========   ====================
Parameters
==========================================
objectId   optional   The id of the object on which to call an instance
                      method.  Either objectId or type is required.

type       optional   The type on which to call a static method.

method     required   The name of the method to call.
========   ========   ====================

The POST body is parsed as JSON and passed to the method as ``context.params``.

By default this API requires write permission on the object or schema.
ACLs for calling methods can be configured as described in :ref:`authorizationSchemaMethods`.

List Type methods
#################

Request::

    GET /listMethods/?objectId=<objectId>
    GET /listMethods/?type=<type>
    GET /listMethods/?type=<type>&static

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=============   ========   ====================
Parameters
===============================================
objectId        optional   The id of the object you want to list methods of.
                           Either objectId or type is required.

type            optional   A Cordra type; depending on the static parameter,
                           this will list static methods on that type, or
                           instance methods on objects of the type.

static          optional   If present, listing methods for a type will list
                           static methods instead of instance methods.
=============   ========   ====================

The HTTP response is a list of strings which are the available method names.


.. _tokenApi:

Access Token API
~~~~~~~~~~~~~~~~

.. warning::

   The following API was introduced in Cordra v2.0.0.
   If you are using an earlier version, please refer to :ref:`legacySessionsApi` for information
   on using cookie-based sessions with the HTTP REST API.

The access token API can be used to authenticate only once to obtain an access token, 
which can then be provided for multiple calls.  This way,
the server does not need to reprocess your authentication credentials with each request, which can
significantly speed up the process of making multiple requests.

Once authenticated using the access token API, the access token should be sent to other APIs using an Authorization Bearer
header. For example::

    Authorization: Bearer ACCESS_TOKEN


Create a new access token
#########################

Request::

    POST /auth/token

POST Data::

    {
        "grant_type": "password",
        "username": <username>,
        "password": <password>,
    }

or

::

    {
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": <JWT_for_keypair_authentication>
    }

The POST data specifies whether the user is authenticating via username/password or via keypair,
depending on the grant_type.  If the grant_type is password, the POST data should contain the
username and password.  See :ref:`auth-with-keys` for the details of the JWT assertion
that must be included with keypair authentication.

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
full                    optional                If ``?full=true`` is specified as a query parameter,
                                                additional fields are included in the response.
=====================   =====================   ====================

.. tabularcolumns:: |\X{3}{7}|\X{4}{7}|

========================   ====================
Response Attribute Name    Description
===============================================
access_token               The newly created access token.

token_type                 Always "Bearer".

active                     Whether or not the token is active; always "true" for successful calls of the /auth/token API.

username                   Username of the authenticated user

userId                     UserId of the authenticated user

typesPermittedToCreate     List of types this user can create; included when ``?full=true`` is specified.

groupIds                   List of groups this user is in; included when ``?full=true`` is specified.
========================   ====================

Get access token information
############################

Request::

    POST /auth/introspect

    {
        "token": <token>
    }

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =====================   ====================
Parameters
====================================================================
full                    optional                If ``?full=true`` is specified as a query parameter,
                                                additional fields are included in the response.
=====================   =====================   ====================

.. tabularcolumns:: |\X{3}{7}|\X{4}{7}|

========================   ====================
Response Attribute Name    Description
===============================================
active                     Whether or not the token is active.

username                   Username of the authenticated user

userId                     UserId of the authenticated user

typesPermittedToCreate     List of types this user can create; included when ``?full=true`` is specified.

groupIds                   List of groups this user is in; included when ``?full=true`` is specified.
========================   ====================

Delete specified access token
#############################

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=============   ====================
Request Headers
====================================
Authorization   Bearer <token>
=============   ====================

Request::

    POST /token/revoke

    {
        "token": <token>
    }

Response::

    {
        "active": false
    }


.. _adminApi:

Administrative APIs
-------------------

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

===================================   ====================
Resource                              Description
==========================================================
GET /schemas/<type>                   Get schema or all schemas.

PUT /schemas/<type>                   Create or update a schema.

DELETE /schemas/<type>                Delete a schema.

POST /uploadObjects                   Bulk upload of objects from
                                      a json file.

PUT /adminPassword                    Change the password for the
                                      admin user.

POST /updateHandles                   Update all handle records for
                                      objects in Cordra.

GET /updateHandles                    Get status of handle update.

POST /reindexBatch                    Reindex all specified objects.

GET /startupStatus                    Accessible even after failed
                                      startup to indicate success or
                                      failure.
===================================   ====================

Schemas API
~~~~~~~~~~~

Retrieve schema
###############

Request::

    GET /schemas
    GET /schemas/<type>

Example
#######

Request::

    GET /schemas/JavaScriptDirectory

Response::

    {
      "type": "object",
      "required": [
        "directory"
      ],
      "properties": {
        "id": {
          "type": "string",
          "cordra": {
            "type": {
              "autoGeneratedField": "handle"
            }
          }
        },
        "directory": {
          "type": "string",
          "title": "Directory",
          "cordra": {
            "referrable": {
              "id": true,
              "payloads": "scripts"
            }
          }
        }
      }
    }

Create or update schema
#######################

Request::

    POST /schemas/<type>

Example
#######

Request::

    POST /schemas/JavaScriptDirectory

Request Body::

    {
      "type": "object",
      "required": [
        "directory"
      ],
      "properties": {
        "id": {
          "type": "string",
          "cordra": {
            "type": {
              "autoGeneratedField": "handle"
            }
          }
        },
        "directory": {
          "type": "string",
          "title": "Directory Changed",
          "cordra": {
            "referrable": {
              "id": true,
              "payloads": "scripts"
            }
          }
        }
      }
    }

Response::

    {
        "msg": "success"
    }

Delete schema
#############

Request::

    DELETE /schemas/<type>

Example
#######

Request::

    DELETE /schemas/JavaScriptDirectory

Response::

    {
        "msg": "success"
    }

Upload Objects API
~~~~~~~~~~~~~~~~~~

Upload objects
##############

Request::

    POST /uploadObjects

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

====================   =======   ====================
Parameters
=====================================================
deleteCurrentObjects   boolean   If true, delete all existing objects before
                                 uploading new objects. Otherwise, update
                                 objects. Default: false
====================   =======   ====================

Example
#######

Request::

    POST /uploadObjects

Request Body::

    {
      "results": [
        {
          "id": "test/171a0606f7c74580fd39",
          "type": "Schema",
          "content": {
            "identifier": "test/171a0606f7c74580fd39",
            "name": "Group",
            "schema": < Schema json omitted for brevity >,
            "javascript": < JavaScript omitted for brevity >
          },
          "metadata": {
            "createdOn": 1535479938849,
            "createdBy": "admin",
            "modifiedOn": 1535479938855,
            "modifiedBy": "admin",
            "txnId": 65
          }
        },
        {
          "id": "test/171a0606f7c74580fd39",
          "type": "Schema",
          "content": {
            "identifier": "test/171a0606f7c74580fd39",
            "name": "Document",
            "schema": < Schema json omitted for brevity >
          },
          "metadata": {
            "createdOn": 1535479938849,
            "createdBy": "admin",
            "modifiedOn": 1535479938855,
            "modifiedBy": "admin",
            "txnId": 65
          }
        },
      ]
    }

Response::

    {
        "msg": "success"
    }

Admin Password API
~~~~~~~~~~~~~~~~~~

Used to update the ``admin`` user password.

Update admin password
#####################

Request::

    PUT /adminPassword

Example
#######

Request Body::

    {
      "password": "newPassword"
    }

Response::

    {
        "success": "true"
    }

Update Handles API
~~~~~~~~~~~~~~~~~~

Used to update the ``admin`` user password.

Start handle update
###################

Request::

    POST /updateHandles

Example
#######

Response::

    {}

Get status of handle update
###########################

Request::

    GET /updateHandles

Example
#######

Response::

    {
        "inProgress": true,
        "total": 123,
        "progress": 52,
        "startTime": 1535479938855,
        "exceptionCount": 0
    }

.. _reindex_batch_api:

Reindex Batch API
~~~~~~~~~~~~~~~~~~

Used to reindex the specified list of objects. Takes a JSON array of object ids to
be reindexed. When using object locking, which is on by default, batch sizes should
be small e.g. 16. However many reindex requests can be sent in parallel.

Reindex batch of objects
########################

Request::

    POST /reindexBatch

============   ========   ====================
Parameters
==============================================
lockObjects    optional   Defaults to 'true'. Locks on object ids while objects
                          are reindexed. You should only set this to 'false' if
                          users are not using the system during reindexing, or
                          it is otherwise possible to guarantee that the
                          objects being reindexed will not be concurrently
                          updated.  If this is possible, performance is
                          improved by setting this to false.
============   ========   ====================

Example
#######

Request Body::

    [
      "test/abc",
      "test/def",
      "test/xyz"
    ]

Response::

    {
        "success": "true"
    }

.. _startup-status:

Startup Status API
~~~~~~~~~~~~~~~~~~

This API provides some information even how far startup progressed even in the event
of startup failure.  This is useful in installations where remote access is easier than
directly looking at log files; however, the amount of information provided is very limited.
It will contain a "state" which is either "UP" or "FAILED", and a "details" which will
contain the status ("UP" or "FAILED") of some of the following, depending on your Cordra
configuration:

- "storage": Cordra's configured storage module
- "indexer": Cordra's configured indexer module
- "zookeeper": subsystem for accessing Zookeeper (for a distributed Cordra)
- "replicationProducer": subsystem for sending transactions to be replicated (using Kafka)
- "replicationConsumer":  subsystem for receiving transactions to be replicated (using Kafka)

Request::

    GET /startupStatus

Response::

    {
      "state": "UP",
      "details": {
        "storage": "UP",
        "indexer": "UP"
      }
    }

Experimental APIs
-----------------

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

===================================   ====================
Resource                              Description
==========================================================
GET /versions/?objectId=<objectId>    Get version information for
                                      a specific object.

POST /versions/?objectId=<objectId>   Create a version of a specific
                                      object.
===================================   ====================


Versioning API
~~~~~~~~~~~~~~

Retrieve object version
#######################

Request::

    GET /versions/?objectId=<objectId>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

========   ========   ====================
Parameters
==========================================
objectId   required   The id of the object you want version information on.
========   ========   ====================

Example
#######

Request::

    GET /versions/?objectId=20.5000.1234/eb3b797f9fd544fb90fb

Response::

    [
        {
            "id": "20.5000.1234/208b07aec73a36b91a1b",
            "type": "Foo",
            "versionOf": "20.5000.1234/eb3b797f9fd544fb90fb",
            "publishedBy": "admin",
            "publishedOn": 1436380157539,
            "isTip": false
        },
        {
            "id": "20.5000.1234/eb3b797f9fd544fb90fb",
            "type": "Foo",
            "modifiedOn": 1433957772377,
            "isTip": true
        }
    ]

Create object version
#####################

Request::

    POST /versions/?objectId=<objectId>

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=============   ========   ====================
Parameters
===============================================
objectId        required   The id of the object you want to create a version of.

versionId       optional   The desired id of the new version.  If omitted Cordra
                           will mint an id.

clonePayloads   optional   If present, the new version object will not contain
                           a copy of the payloads.
=============   ========   ====================


Example
#######

Request::

    POST /versions/?objectId=20.5000.1234/eb3b797f9fd544fb90fb

Response::

    {
        "id": "20.5000.1234/37b4ac94ba3e14665e04",
        "type": "Foo",
        "versionOf": "20.5000.1234/eb3b797f9fd544fb90fb",
        "publishedBy": "admin",
        "publishedOn": 1436380685442,
        "isTip": false
    }

