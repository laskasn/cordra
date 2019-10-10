.. _aa:

Authentication and Access Controls
==================================

The Cordra software has a built-in access control framework. Each object may have an Access Control List (ACL)
listing users and groups permitted to read and/or write the object. Additional ACLs configured in different places
provide defaults and specify which users and groups may create objects of that type. Both users and groups are
represented as objects, with a group being a collection of user object identifiers. Also, users and groups are
not specific ``types`` in the system, but rather decorations around other types of objects that make those objects
behave like users and groups for purposes of ACLs. The rest of the documentation may refer to objects as user objects
and group objects, but those cases imply this nuance.

Cordra comes with pre-defined user and group objects, but it is possible to modify those or create your own.
For more information, see :ref:`userManagement`.

Authentication
--------------

Users can authenticate using passwords or public/private key pairs over the REST API or DOIP. 
In the REST API, users can authenticate using HTTP Basic Auth for passwords and
HTTP Bearer JWT token for keys. An access token API is also available in REST. See :ref:`tokenApi` for more details.

The system enforces uniqueness of usernames for user objects. Since these objects are like any other object
in the Cordra system, the properties associated with user objects can be changed according to the needs of any
particular administrative environment; for instance, users can be associated with email addresses or phone numbers.

Administrators, associated with the special username ``admin`` can authenticate using passwords and/or keys.
If authenticating over keys, the public key for the admin user should be managed in the Design object under
a property called ``adminPublicKey``.

.. warning::

   As of Cordra v2.0.0, all authentication requests must be made over
   a secure HTTPS connection. To allow authentication over insecure channels, you can add
   "allowInsecureAuthentication":true to the Cordra design object.

Authentication via Passwords
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

User passwords are managed as part of the user objects. However, passwords are hashed and salted and
stored separately from the rest of the information managed by the object. As such, the value of the
password property within the JSON data is always the empty string when the object is resolved.

Cordra matches the password supplied by the user against the stored password, as part of user authentication.

.. _auth-with-keys:

Authentication via Keys
~~~~~~~~~~~~~~~~~~~~~~~

Cordra offers a more secure way to authenticate users. Users can also authenticate using public/private key pairs
where the public keys are stored in either Cordra objects or Handle records.

Specifically, the public key may be stored as a JSON Web Key (JWK) as part of a user object or may be stored in an
HS_PUBKEY value on a Handle record. In order to store a public key in a Cordra object that can be used for authentication,
the schema for the type of object containing the public key needs the Cordra-specific schema attribute
"auth": "publicKey". When authenticating using a public key on a Cordra object, either the username or the
Cordra object id may be specified as the issuer in the Authorization header. When authenticating with an
HS_PUBKEY in a handle record, the handle of the record should be used as the issuer in the Authorization header.
See below for how to specify the issuer syntactically.

To authenticate, users should send an Authorization header where the value of the header is
Bearer followed by a serialized JSON Web Signature::

    Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJ...

The JWS must be a self-issued JWT with at least an ``"iss"`` claim (the
identity that is authenticating), and an ``"exp"`` claim at most one hour in the future (a minimum of a few minutes
to avoid clock skew issues would generally suffice).  If the ``"sub"`` claim is present it must be identical with the
``"iss"`` claim.

The JWT can use other safeguards to protect replay attacks. For example, the JWT can optionally include a ``"jti"``
claim by the client, so Cordra will know to prevent reuse of the same JWT for subsequent requests.
(Note: Cordra instances deployed for a distributed setup do not currently support the jti claim.)

The JWT can also optionally have an ``"aud"`` claim, which contains an identifier for the Cordra instance, to prevent
the reuse of the authentication claim at other Cordra instances. Cordra can be configured with identities allowed
in the ``"aud"`` claim in the Design object's ``"ids"`` property. This can be added in the Cordra UI, or set on
startup by including a ``repoInit.json`` file in the Cordra data directory with the following contents:

.. code-block:: js

   {
     "design": {
       "ids": [ "20.500.123/cordra" ]
     }
   }

For more information on editing the Design object, see :ref:`design-object`.

In general, the ``"exp"``, ``"jti"``, and ``"aud"`` restrictions are to prevent reuse of the authentication claims,
but as long as the suggested authentication claim is sent over TLS, it is already more secure than sending a password
because no secrets are exchanged in this Bearer method compared to the passwords case, but rather the possession
of secrets is claimed with expiration time to bound the use of those claims indefinitely.

Example JWT for Bearer authentication::

    {
        "iss": "20.500.123/admin",
        "sub": "20.500.123/admin",
        "jti": "5d21776da77adb89528d",
        "aud": "20.500.123/cordra",
        "exp": 1533139594
    }

For further information about the claims used in the JWT for Cordra keypair authentication, see
`RFC 7519 <https://tools.ietf.org/html/rfc7519>`_.

.. _authorization:

Authorization
-------------

Authorization is enabled primarily using access control lists (ACLs) as defined below. In some situations,
contextual access to objects where information beyond the user or group id should be considered for providing object
access. Please refer to :ref:`type-methods` for leveraging lifecycle hooks to handle those special authorization situations.
Furthermore, ACLs are distinguished in terms of read operations and write operations. For enabling access controls at
a finer operation granularity than reads or writes, :ref:`type-methods` should be leveraged.

For ACL-based authorization, a single inheritance structure is followed: ACLs specified at the *object* level
overrides the ACLs specified at the *type* level, which are overriden by the ACLs specified at the *system* level.
Overrides are complete replacements, not merges.

Both user object identifiers and group object identifiers play a role here.
For more information on creating users and groups using the Cordra UI, see :ref:`userManagement`.

Authorization is controlled by access control lists. Each ACL is an array of strings. Those strings could be the
handle identifiers of specific users or specific groups or they could be one of a set of ACL keywords below. If the ACL is
left blank, then only admin can perform the operation. In the context of ACL-based authorizations, operations are
categorized as only read and write.

.. tabularcolumns:: |\X{1}{6}|\X{5}{6}|

=================================   ====================
ACL Keyword                         Description
=================================   ====================
public                              Anyone can perform the operation.
                                    They do not need to be
                                    authenticated.

authenticated                       Any authenticated user can
                                    perform the operation. This only
                                    applies to users with user objects
                                    in Cordra, not to arbitrary
                                    handle-identified users who
                                    authenticate using public/private
                                    keypair. The handle-identified
                                    users should be explicitly given
                                    access outside of the keywords
                                    described here.

creator                             Only the creator of the object
                                    can perform the operation.

self                                Only a user with the same id as
                                    the object of interest can perform the
                                    operation. Typically used on
                                    defaultAclWrite setting on user objects.
=================================   ====================

Each object has an ACL for readers and an ACL for writers. Readers can also view the ACLs; writers can also
modify the ACLs.

Example::

    {
        "readers": [
            "20.5000.215/73675debcd8a436be48e"
        ],
        "writers": [
            "20.5000.215/73675debcd8a436be48e"
        ]
    }


In addition to being able to specify an explicit access control list on instances of individual objects, each type
can have default ACLs for objects of that type, as well as an ACL indicating who can create new objects of that
type. The type-level read and write ACLs apply only to objects which do not specify their own object-level ACLs.
Finally, the software can be configured with default ACLs which apply across all types which do not specify their
own ACLs.

The administrative configuration APIs are authorized only for the special user "admin". See :ref:`adminApi`.

Cordra allows access to the ACLs (represented as JSON) of an object, with two properties ``readers`` and ``writers``,
each an array of strings. The type-level and default ACLs are configured by specifying a JSON
structure as well.

Example::

    {
        "schemaAcls": {
            "User": {
                "defaultAclRead": [ "public" ],
                "defaultAclWrite": [ "self" ],
                "aclCreate": []
            },
            "Document": {
                "defaultAclRead": [ "public" ],
                "defaultAclWrite": [ "creator" ],
                "aclCreate": [ "public" ]
            }
        },
        "defaultAcls": {
            "defaultAclRead": [ "public" ],
            "defaultAclWrite": [ "creator" ],
            "aclCreate": []
        }
    }


**NOTE:** The JSON representation of ACL has changed in Cordra v2. In v1, they were called ``read`` and ``write``. In
v2, the properties are called ``readers`` and ``writers`` respectively.

.. _authorizationSchemaMethods:

Authorization for Type Methods
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Type methods are Cordra's way of enabling custom operations to be added to the system. And these operations or methods
can be enabled in the context of a given type; hence the name *Type methods*. See :ref:`type-methods` for more details.

In addition to restricting who can read or write to an object, ACLs can also be defined who can execute these methods.
ACLs for calling Type methods can be defined on the ``"schemaAcls"`` or ``"defaultAcls"`` of the global authorization
configuration. The property ``"aclMethods"`` determines the authorization. If ``"aclMethods"`` is missing on the
``"schemaAcls"`` entry for the type in question, then the aclMethods property in the ``"defaultAcls"`` is considered. If
the property is missing there too, then Cordra assumes that all authorized writers of the instance object
(for an instance method) or the schema/type object (for a static method) are authorized to call the method.

The property ``"aclMethods"`` is an object with properties ``"instance"`` and ``"static"``, each a map from method name
to ACL, as well as a property ``"default"``, an object with properties ``"instance"`` and ``"static"``, each an ACL.
The default method ACLs apply to methods which are not named explicitly under ``"instance"`` or ``"static"``.

Method ACLs can use the additional keywords ``"readers"`` and ``"writers"``, which authorize respectively all the
authorized readers or writers of the instance object (for an instance method) or schema object (for a static method).
In the absence of any ``"aclMethods"`` entry, all methods are considered to have ACL ``[ "writers" ]``.  If
``"aclMethods"`` is defined but a method is missing and no default is defined, it is considered to have ACL ``[ ]``
(admin access only).

Example::

    {
        "schemaAcls": {
            "User": {
                "defaultAclRead": [ "public" ],
                "defaultAclWrite": [ "self" ],
                "aclCreate": []
            },
            "Document": {
                "defaultAclRead": [ "public" ],
                "defaultAclWrite": [ "creator" ],
                "aclCreate": [ "public" ],
                "aclMethods": {
                    "static": {
                        "exampleStaticMethod": [ "public" ]
                    },
                    "instance": {
                        "exampleInstanceMethod": [ "authenticated" ]
                    },
                    "default": {
                        "instance": [ "writers" ]
                    }
                }
            }
        },
        "defaultAcls": {
            "defaultAclRead": [ "public" ],
            "defaultAclWrite": [ "creator" ],
            "aclCreate": []
        }
    }

In this example, static method ``"exampleStaticMethod"`` on type Document can be called by anyone, even anonymously;
instance method ``"exampleInstanceMethod"`` on objects of type Document can be called by any authenticated user. Other
instance methods on objects of type Document can only be called by authorized writers of the object. Since the
``"static"`` property on ``"default"`` is missing, other static methods can only be called by admin. On types other than
Document, all methods can be called by authorized writers.
