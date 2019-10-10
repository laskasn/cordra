:orphan:

.. _legacySessionsApi:

Legacy Sessions API
===================

.. warning::

   This API is disabled by default in Cordra v2.0.0. To
   re-enable it, you can add "useLegacySessionsApi":true to the Cordra design object.

The sessions API requires sending back a session cookie (cookie name JSESSIONID) as well as a
separate X-Csrf-Token header for CSRF mitigation. The CSRF token is also returned to the user
as a cookie (cookie name Csrf-token); thus the server will in general return two Set-Cookie headers,
one for the JSESSIONID cookie and one for the Csrf-token cookie. Both values must be returned
in order for session authentication to work; the JSESSIONID cookie must be returned as a standard
Cookie: header, and the CSRF token must be returned in a special X-Csrf-Token header.

Cookie Configuration
--------------------

REST API clients can leverage sessions to avoid repetitive authentication. Cookies configuration can be set on the
design object to control certain properties of the two cookies set by the server for session-based authentication,
named JSESSIONID and Csrf-token.

To understand the rest of this cookies discussion, you will need to understand how browser cookies work.

By default, the HttpOnly property on the JSESSIONID cookie is set to true. And both cookies have "Path" the same
as the base URI path where Cordra is serving from relative to the domain name. In general, the Csrf-token cookie
needs to be available to browser-based JavaScript. If the browser-based JavaScript is hosted at Path=/ and
Cordra is at Path=/cordra, then the default settings will not work. In this case the following design change will help:

::

    {
        "cookies": {
            "csrfToken": {
                "path": "/"
            }
        }
    }

This will cause the Csrf-token cookie to be set by the server with Path=/, allowing the JavaScript at /
to access it.

It is also possible to set in the Design object the "cookies.csrfToken.httpOnly" and "cookies.csrfToken.secure"
properties (as true or false). Likewise, the same three properties (path, httpOnly, and secure) on "cookies.jsessionid"
can be set in the Design object. In general, this is not necessary or recommended; by default, the JSESSIONID cookie
will be HttpOnly and Secure (when served over HTTPS).

API Details
-----------

.. tabularcolumns:: |\X{4}{7}|\X{3}{7}|

=====================================================================================   ====================
Resource                                                                                Description
=====================================================================================   ====================
`GET /sessions/this <#get-session-information>`_                                        Retrieve current session.

`POST /sessions/ <#create-new-session>`_                                                Create a new session.

`DELETE /sessions/this <#delete-current-session>`_                                      Delete current session.
=====================================================================================   ====================

Get session information
~~~~~~~~~~~~~~~~~~~~~~~

Request::

    GET /sessions/this

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====   ========   ====================
Parameters
=======================================
full    optional   If ``true``, include ``typesPermittedToCreate`` and
                   ``groupIds`` for current user in response.
=====   ========   ====================

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=====================   ====================
Request Headers
============================================
X-Csrf-Token            Value from Csrf-token cookie
=====================   ====================

.. tabularcolumns:: |\X{3}{7}|\X{4}{7}|

========================   ====================
Response Attribute Name    Description
===============================================
isActiveSession            Whether or not the session is active.

username                   Username of currently logged in user

userId                     UserId of currently logged in user

typesPermittedToCreate     List of types this user can create.

groupIds                   List of groups this user is in.
========================   ====================

Create new session
~~~~~~~~~~~~~~~~~~

Request::

    POST /sessions/

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=============   ====================
Request Headers
====================================
Authorization   Should be a Basic or Bearer auth header
=============   ====================

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

=====================   ====================
Response Headers
============================================
Set-Cookie              Sets cookies needed for CSRF
=====================   ====================

.. tabularcolumns:: |\X{2}{7}|\X{5}{7}|

========================   ====================
Response Attribute Name    Description
===============================================
isActiveSession            Whether or not the session is active.

username                   Username of currently logged in user

userId                     UserId of currently logged in user

typesPermittedToCreate     List of types this user can create.

groupIds                   List of groups this user is in.
========================   ====================

Delete current session
~~~~~~~~~~~~~~~~~~~~~~

Request::

    DELETE /sessions/this

Response::

    {
        "isActiveSession": false,
        "typesPermittedToCreate": []
    }

