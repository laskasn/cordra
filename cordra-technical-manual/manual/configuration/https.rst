.. _https_configuration:

Configuring HTTPS Keys
======================

Cordra provides a REST interface along with a web interface. The HTTP endpoint for those two interfaces is strongly
recommended to be based on HTTPS. We describe below how to generate the keys necessary to enable HTTPS.

There are three methods for configuring HTTPS keys in Cordra:

#. using automatically-generated, self-signed keys (default)
#. replacing the generated keys in the ``data`` directory with specific keys, including a browser-valid TLS certificate
#. configuring Cordra to use keys located elsewhere in the filesystem

The default behavior allows Cordra to serve HTTPS traffic, but users will see a browser warning about the self-signed
key. To remove this warning, configure a key as described below.

Replacing Files in Default Location
-----------------------------------

If you have a signed set of keys, along with a certificate chain file, you can simply copy those files into your Cordra
``data`` directory, overwriting the generated keys. Copy the private key to ``httpsPrivateKey.pem`` and the certificate
to ``httpsCertificate.pem``. You will need to restart in order for Cordra to recognize the new keys.

Assuming your keys are correctly signed, you should now be able to view the Cordra web interface without any errors or
warnings displayed by the web browser.

Configure Key Location
----------------------

Instead of copying your keys into Cordra, you can put information about the location of your keys on disk in the Cordra
``config.json`` file. An advantage to using this method to configure your keys is that Cordra will automatically reload
the keys if the files are modified. This is useful if you are using keys that change frequently (for example, keys
from `Let's Encrypt <https://letsencrypt.org/>`__).

PEM Files
~~~~~~~~~

To configure Cordra to use standard PEM-formatted files, add the following to parameters to the ``config.json`` file.

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=========================   =================   ====================
Parameters
====================================================================
httpsPrivKeyFile            required            Path to the private key file.
                                                Must be encoded in the PKCS#8 format.

httpsCertificateChainFile   required            Path to the certificate chain file.

httpsKeyPassword            optional            Password to use for decrypting the private key, if needed.
=========================   =================   ====================

Example configuration::

    {
      "httpPort": 8080,
      "httpsPort": 8443,
      "listenAddress": "0.0.0.0",
      "httpsPrivKeyFile": "/path/to/keys/example.com.key",
      "httpsCertificateChainFile": "/path/to/keys/example.com.crt"
    }


KeyStore Files
~~~~~~~~~~~~~~

You can also configure Cordra to use a keystore. Here are the parameters to add to the ``config.json`` file.

.. tabularcolumns:: |\X{2}{7}|\X{1}{7}|\X{4}{7}|

=====================   =================   ====================
Parameters
================================================================
httpsKeyStoreFile       required            Path to the keystore file.

httpsAlias              required            Alias for the keystore entry to use.

httpsKeyStorePassword   optional            Password for the keystore.
                                            One of this or httpsKeyPassword is required.

httpsKeyPassword        optional            Password for the key referenced by httpsAlias.
                                            One of this or httpsKeyPassword is required.
=====================   =================   ====================

Keystores can have a password and a separate (and, optionally, different) password for each key in the store. If you use
the same password for both the keystore and the key at the given alias, you only need to set one of
httpsKeyStorePassword and httpsKeyPassword in your configuration. If only one of the two properties is set, the value of
that property will be used for both properties. If both are set, each one will be used as configured.

In the follow example configuration configuration, the password ``password`` would be used to decrypt both the keystore
and the private key at alias ``example``::

    {
      "httpPort": 8080,
      "httpsPort": 8443,
      "listenAddress": "0.0.0.0",
      "httpsKeyStoreFile": "/path/to/example.com.keystore",
      "httpsAlias": "example",
      "httpsKeyStorePassword": "password"
    }

