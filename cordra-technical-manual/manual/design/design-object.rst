.. _design-object:

Design Object
=============

The Design object is a central location where much of the Cordra configuration is stored. For example, the
configuration for the UI, authorization, and handle minting are all stored on the Design object. When logged in as a
Cordra admin into the UI, the Design Object Admin menu item provides you a way to edit this object directly. The Design
object could also be updated using a configuration file on startup; see :ref:`repo-init`.

Here are the properties available in the Design object for fine-tuning Cordra behavior:

.. tabularcolumns:: |\X{3}{7}|\X{4}{7}|

=====================================   ====================
Property name                           Description
=====================================   ====================
ids                                     See :ref:`auth-with-keys`.

useLegacySessionsApi                    See :ref:`legacySessionsApi`

useLegacyContentOnlyJavaScriptHooks     See :ref:`legacy-js`

useLegacySearchPageSizeZeroReturnsAll   If true, restores former behavior
                                        where a search with pageSize=0
                                        returns all results.  By default
                                        a search with pageSize=0 returns
                                        the number of matched objects but
                                        no object content.

enableVersionEdits                      If true, version objects can be edited.
                                        By default, they are immutable.

includePayloadsInReplicationMessages    If true, payloads are included in Kafka messages
                                        produced by configuration replicationProducers.
                                        By default the payloads are omitted.

disableAuthenticationBackOff            By default, Cordra will slow down
                                        authentication attempts for a
                                        user after receiving an incorrect
                                        password for that user, up to a
                                        maximum of 5 seconds.  Setting
                                        this to true disables the delay.

allowInsecureAuthentication             Allow authentication requests over HTTP. By default,
                                        only HTTPS is allowed.

adminPublicKey                          JSON Web Key that can be used
                                        to log in as admin user.

uiConfig                                See :ref:`ui-config`.

authConfig                              See :ref:`auth-config`.

handleMintingConfig                     See :ref:`handle-integration`.

handleServerConfig                      See :ref:`handle-integration`.

doip                                    See :ref:`doip`.

javascript                              See :ref:`design-javascript`.
=====================================   ====================

.. _repo-init:

repoInit.json
-------------

All or part of the Design object can also be modified on startup by including a ``repoInit.json`` file in the
Cordra ``data`` directory. The JSON structure under the ``design`` property in the repoInit.json file should match
what is expected in the Design object. For example, to add ids that are useful for :ref:`auth-with-keys`, you could
include the following in the repoInit.json file:

.. code-block:: js

    {
        "design": {
            "ids": [ "20.500.123/cordra" ]
        }
    }
