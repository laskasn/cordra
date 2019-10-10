.. _handle-integration:

Handle Integration
==================

Cordra allots identifiers to digital objects. See :ref:`identifiers` for details on how those identifiers are allotted.
In some cases, Cordra may automatically generate the identifier, in which case it will use a prefix configured as discussed
below in :ref:`handle-minting-configuration`.

We describe below three different ways in which those digital object identifiers, aka ``handles``, can be made resolvable
via the Identifier/Resolution Protocol (IRP).

IRP Interface
-------------

Cordra provides a resolution interface to identifiers. To enable this, edit the ``handleServerConfig``
section of the Cordra :ref:`design-object`. The IRP interface will only be started when the ``enabled`` flag in this
section is set to true, which is the case by default.
 
In the ``handleServerConfig`` section, set the ``listenAddress``, ``tcpPort``, ``externalAddress``,
, and ``externalTcpPort`` as needed based on your Cordra and network configuration.  ``externalAddress`` and
``externalTcpPort`` will be how the users connect to the IRP interface; they can be omitted if they match
``listenAddress`` and ``tcpPort``. You can set ``logAccesses`` to true in order to obtain IRP access logs.

Cordra automatically applies the updated settings once saved; you do not need to restart Cordra to activate the settings.

The IRP requires a public-private key pair to identify the provider (in this case, the Cordra instance) to clients.
The keys are managed as files in the Cordra data directory: handlePrivateKey.bin and handlePublicKey.bin. A new key pair
will be created the first time the IRP interface is enabled.

In order for Cordra to return useful handle records, you should set the :ref:`handle-minting-configuration`
on the Design object.

If you wish to register your IRP interface with an `MPA <https://www.dona.net/mpas>`__, you or they will need to obtain
the IRP interface site information, which is provided by Cordra in response to a ``get site info`` request; this can be obtained
using the ``hdl-getsiteinfo`` script provided in the ``bin`` directory of the Cordra distribution.


Handle Server
-------------

In certain cases it may be useful to have Cordra rely on external IRP interfaces rather than use its own. For example,
your Cordra server may be part of a community that provides a common IRP service. Or you may have handles other than those
that Cordra allots and you prefer to manage all those handles using an existing IRP service.

You can setup a IRP service using the handle server software `here <https://handle.net/download_hnr.html>`__..

You will need a Handle key pair to use with Cordra. You can either use the admin keys that were generated during the
Handle server installation (admpub.bin and admpriv.bin), or generate a new set of keys using the Handle tools. For
example, run the following script from the Handle distribution directory to generate new keys and save them to files
called ``privatekey`` and ``publickey``::

    ./bin/hdl-keygen privatekey publickey

``hdl-keygen`` script is also made available in the ``bin`` directory of the Cordra distribution.

You should copy the generated keys to the Cordra ``data`` directory and make sure they are named ``privatekey`` and
``publickey``. Then, create an admin handle record on your handle server that includes the Cordra public key in the
HS_PUBKEY value. That handle will be used as the administrator identity by Cordra when it contacts the handle server.
The handle server should be configured so that that identity is authorized to create handle records.

You should edit the Cordra :ref:`design-object`, specifically the ``handleMintingConfig`` section, to set the ``handleAdminIdentity``
to be the index and handle of the Cordra admin handle record you created.  You will also need to set either ``baseUri``
or ``javascript`` on the ``handleMintingConfig`` as described below, and then save the Design object.

Cordra will automatically register handle records with the external Handle Server for digital objects that are created
in the future. To update handle records pertaining to existing digital objects, login into the Cordra web interface as admin,
select ``Admin -> Handle Records`` from the menu, and click on ``Update All Handles`` button to have Cordra update the
handle records for existing digital objects.


Handle Server Storage
---------------------

This option was relevant when Cordra software did not provide a built-in IRP interface (option one above). Per this option,
you can configure an external handle server to use Cordra as its storage.

Like the previous option, you can setup an external handle server using the instructions stated
`here <https://handle.net/download_hnr.html>`__. You will then have to install the ``cordra-client-handle-storage``
software, included with the Cordra distribution, on the handle server. Installation instructions are included in the
README.txt file from the cordra-client-handle-storage directory.


.. _handle-minting-configuration:

Handle Minting Configuration
----------------------------

The :ref:`design-object` includes a section ``handleMintingConfig`` which allows configuring both how Cordra automatically
generates the identifiers themselves, as well as the values of the handle records.

The ``prefix`` property gives the default handle prefix used by Cordra when generating identifiers.  If :ref:`generateId` is used
to specify identifiers, or calls to the Cordra APIs always specify the identifier of newly created objects, this will have no effect.
Otherwise this should be set to an appropriate prefix.  

Other properties specify the values of the handle records.  
You must set either the ``baseUri`` or ``javascript`` properties in order to have Cordra produce handle records during
IRP resolution. All these properties are available via the web interface once you login as admin: select
``Admin -> Handle Records`` from the menu to access the page to set these two values.

``baseUri`` is useful for handle records to return URLs that handle web proxy services (e.g., http://hdl.handle.net ) can
use to redirect web browsers to the Cordra's REST endpoint or its web interface. See the next sub-section for details.

``javascript`` is the :ref:`createHandleValues`. An example is :ref:`here <createHandleValuesExample>`. You can use this
hook to return desired handle records beyond just returning URLs.

By default, Cordra returns values that are useful for DOIP clients to auto-locate digital objects via the Cordra's DOIP interface.
You can turn off Cordra from returning these values by setting the property ``omitDoipServiceHandleValue`` in the Design Object within the section
``handleMintingConfig`` to ``true``.

Setting the Cordra Base URI
~~~~~~~~~~~~~~~~~~~~~~~~~~~

By using ``baseUri``, handles minted by Cordra will be associated with handle records that consist
of a value that handle web proxies can use to redirect the web browser to the Cordra's REST API and/or user interface.
The configured base URI that will be used for the URIs in the generated handle records.

**Note:**  The Cordra base URI must end with a slash.

By default, handles will redirect to the Cordra web interface, and allow a query parameter (``locatt=view:json``) to
redirect to the JSON of the Cordra object. Handle records can be further configured to allow redirection to
payloads or to a URL included in the JSON of the Cordra object. The configuration of handle records includes ``baseUri``
and optionally ``defaultLinks`` and/or ``schemaSpecificLinks``.  ``defaultLinks`` is an array of objects indicating
which links will be included in the handle records; ``schemaSpecificLinks`` is an object where each property name is a
type, and the corresponding property value is an array of objects indicating links. Each link has a ``type`` which is
one of the following four options:

-  ``json``, the JSON of the Cordra object
-  ``ui``, the Cordra UI for the object
-  ``payload``, a payload attached to the object; the link must include
   either ``specific`` indicating the payload name, or ``all: true``
   indicating that links should be generated for all payloads
-  ``url``, indicating a URL embedded in the JSON of the Cordra object;
   the link must include an property ``specific`` indicating the JSON
   pointer to the URL.

Each link can specify ``primary: true`` to indicate that it should be the
primary redirection. Multiple links with ``primary: true`` will result in
one chosen at random when resolved by hdl.handle.net. Non-primary links
may be accessed using query parameter ``locatt=view:<link>`` where ``<link>``
is either ``json``, ``ui``, the name of a payload, or the JSON pointer of a URL.

An example handle minting configuration::

    {
        "prefix": "20.500.123",
        "baseUri": "http://localhost:8080/",
        "defaultLinks": [
            {
                "type": "json",
                "primary": false
            },
            {
                "type": "ui",
                "primary": false
            },
            {
                "type": "payload",
                "specific": "file",
                "primary": true
            },
            {
                "type": "url",
                "specific": "/url",
                "primary": false
            }
        ]
    }

If ``defaultLinks`` is omitted, Cordra will use a primary ``ui`` link and a non-primary ``json`` link.