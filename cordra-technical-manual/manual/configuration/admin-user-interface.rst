.. _adminUI:

Administrative Web UI
=====================

Cordra's web interface enables users to create, retrieve, update, delete, search, and protect digital objects. When
logged into the Cordra web UI as an administrator, you will see an additional "Admin" dropdown menu
at the top of the screen.

.. image:: ../_static/admin/admin_menu.png
        :align: center
        :alt: Admin Dropdown Menu

Each of these options can be used to modify the how Cordra looks and functions. The options are described below.

Types
-----

.. image:: ../_static/admin/types.png
        :align: center
        :alt: Types Admin

The Types page can be used to add and/or modify the types stored in Cordra. A list of types is shown. There are three
default types. To edit a type, click on its name. A editor will appear, showing the schema and JavaScript for the type.
Make any changes you would like, and then click the "Save" button.

New types can be added either one at a time or in bulk. To add an individual type, click the "Add"
button. In the dialog that pops up, choose a name for the type and a template. If you would like for an example JavaScript
to be populated in the editor, select the "Include example JavaScript" checkbox. Then click the "Add" button.
The dialog will close, and the new type will be available in the editing interface. Make any additional changes
required, and then click the "Save" button to save the new type.

To add multiple types at once, you will first need JSON file containing the types. Here is an example file::

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

The format of the file is similar to the format of the response to an object query. You can download json for
types currently in Cordra using the Objects API::

    GET /objects/?query=type%3A"Schema"

The results of this query can be edited to create a new file for upload. Extra fields like pageNum and pageSize
do not need to be removed.

To upload the types file, first click the "Load from file" button in the Types admin UI. Next, select the
file to upload. If you would like to delete existing types, check the checkbox indicting such. If you choose
not to delete existing types first, an error will be throw if you try to upload a duplicate type. Click the "Load"
button to load the types into Cordra.

Design Object
-------------

See :ref:`design-object` for details.

.. _ui-config:

UI Menu
-------

Some Web UI configuration is stored as JSON within Cordra. Clicking on the UI menu will bring up an
editor that can be used to modify this configuration.

Example UI Configuration::

    {
        "title": "RepositoryTest",
        "allowUserToSpecifySuffixOnCreate": false,
        "initialFragment": "urls/intro.html",
        "relationshipsButtonText": "Show Relationships",
        "navBarLinks": [
            {
                "type": "query",
                "title": "All",
                "query": "*:*",
                "sortFields": "/name"
            },
            {
                "type": "typeDropdown",
                "title": "Types"
            }
        ]
    }

Here are the attributes available in the UI configuration object.

.. tabularcolumns:: |\X{3}{7}|\X{4}{7}|

=================================   ====================
Attribute name                      Description
=================================   ====================
title                               The text used in the title bar to
                                    identify the service.

relationshipsButtonText             The text shown on the button used
                                    to show the relationships between
                                    objects.

allowUserToSpecifySuffixOnCreate    Provides a input box for the
                                    suffix of the object id when
                                    creating objects. The prefix is
                                    set in the handleMintingConfig.

allowUserToSpecifyHandleOnCreate    Provides a input box for the
                                    complete object id when
                                    creating objects.

initialQuery                        A query to be loaded if none is
                                    present when the app is loaded
                                    loaded.

initialFragment                     A hash fragment to be loaded if
                                    none is present when the app is
                                    loaded. This can be used to run a
                                    query on page load or show a
                                    document.

initialSortFields                   Sort fields to use in the UI if
                                    none is specified.

initialFilter                       Filter to use in the UI if
                                    none is specified.

hideTypeInObjectEditor              Do not display object type under
                                    object ID in UI when editing.

numTypesForCreateDropdown           Number of types to display in the
                                    creation dropdown. If more than
                                    this number are available, a
                                    search interface will be shown
                                    instead.

aclUiSearchTypes                    A list of types to be used in the
                                    UI for editing ACLs; typically
                                    the list of types which represent
                                    users and groups.

navBarLinks                         An array of objects used for
                                    adding links to the navigation
                                    bar. Details below.

searchResults                       Configuration for search results
                                    Details below.
=================================   ====================

navBarLinks
~~~~~~~~~~~

.. tabularcolumns:: |\X{2}{6}|\X{4}{6}|

=================================   ====================
Attribute name                      Description
=================================   ====================
type                                Can be ``query``, ``typeDropdown``
                                    or ``url``.

title                               The text used on the link.

query                               If the type is ``query`` this
                                    attribute holds the query to run.

sortFields                          Sort fields for the query
                                    results.

url                                 If the type is ``url`` this
                                    attribute holds the url.
=================================   ====================

searchResults
~~~~~~~~~~~~~

.. tabularcolumns:: |\X{2}{6}|\X{4}{6}|

=================================   ====================
Attribute name                      Description
=================================   ====================
includeType                         Include type in results display.

includeModifiedDate                 Include modification date in
                                    results display.

includeCreatedDate                  Include creation date in
                                    results display.
=================================   ====================

.. _auth-config:

Authorization Menu
------------------

Default access control lists are stored as JSON within Cordra. Clicking on the Authorization menu
will bring up an editor that can be used to modify this configuration.

Example Authorization Configuration::

    {
      "schemaAcls": {
        "User": {
          "defaultAclRead": [
            "public"
          ],
          "defaultAclWrite": [
            "self"
          ],
          "aclCreate": []
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

For more information on configuring ACLs in Cordra, see :ref:`authorization`.

Handle Records Menu
-------------------

This screen can be used to modify Cordra's Handle minting configuration. Once the configuration is modified, you can
use the "Update All Handles" button to propagate changes to the affects Handle records. For more information on
configuring Cordra and Handle integration, see :ref:`handle-integration`.

Security Menu
-------------

This screen can be used to reset the password for the built-in Cordra ``admin`` user.

.. _design-javascript:

Design JavaScript Menu
----------------------

JavaScript can be added to the Design object for the purposes of programmatically generating object ids when creating
new objects. see :ref:`generateId`.
