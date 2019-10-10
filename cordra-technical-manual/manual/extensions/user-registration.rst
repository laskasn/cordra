.. _user_registration:

User Registration
=================

Cordra software enables only authenticated users, including the admin, to be able to create digital objects. Since users
are also represented as digital objects, it may be necessary to allow new users to initiate the creation of their
digital objects, while disallowing the creation of any other digital objects.

This section describes how to build a user registration workflow within Cordra that enables new users to create their
accounts in Cordra and subsequently activate their accounts. Instead of allowing any unauthenticated user to create an
account in one step, we will bind a user account in Cordra to the user's email address.

In particular, when a request for the creation of a new account (i.e., user object) is received, Cordra generates a
random string (called activation key) and sends an email to the requesting user's email address. Only that user will then
be in possession of both the activation key and the information supplied with the creation request. Cordra verifies
that that is the case and completes the user account creation.

Specifically, the following features are considered for this user registration workflow:

* Allow new users to create an account along with their email address in Cordra by themselves. Only admins can create
  user accounts by default.
* Cordra to send emails with activation keys for users to confirm that they initiated the account creation.
* Allow users to activate their account without any admin intervention using the activation key sent to their email
  address. (This step implicitly also confirms that the users have access to emails corresponding to the registered
  email address).

We will start this description with a default Cordra distribution and highlight all of the changes necessary to add the
desired features. Specifically, we will need to:

* Modify the authorization for the User schema so that unauthenticated users can create User objects.
* Add :ref:`type-methods` in JavaScript to the User schema for sending emails and confirming accounts.
* Add the necessary support files to the Cordra ``data`` directory to enable sending emails through a Type method.

Steps
-----

Get Cordra
~~~~~~~~~~

You should download the default Cordra distribution `here <https://www.cordra.org/download.html>`__.
Once you have downloaded the zip file, unzip it, and start Cordra as explained :ref:`here <single_instance_deployment>`.
Once the startup process is complete, you should be be able to access the Cordra web interface at https://localhost:8443.

Enable Email Support
~~~~~~~~~~~~~~~~~~~~

For testing purposes, you can skip enabling the email support. If you do not set up email support, be sure your
JavaScript method prints the activation key to the terminal console where the Cordra process is launched, so that you
can use it to activate the user account, for testing purposes. In production, however, email support should be enabled
as described in :ref:`sendingEmails`.

Modify the Authorization Config
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, unauthenticated users cannot create User objects in Cordra. To allow users to create their own accounts,
log into the Cordra UI as the ``admin`` user, using the password you created on first startup. Choose the
``Authorization`` menu item from the ``Admin`` menu at the top of the page. You will be presented with a JSON
representing the current default access controls. Replace the existing JSON with the following:

.. code-block:: json
    :linenos:

    {
      "schemaAcls": {
        "User": {
          "defaultAclRead": [ "public" ],
          "defaultAclWrite": [ "self" ],
          "aclCreate": [ "public" ],
          "aclMethods": {
            "instance": {
              "activateAccountIfKeyIsValid": [ "public" ]
            },
            "default": {
              "instance": []
            }
          }
        },
        "CordraDesign": {
          "defaultAclRead": [ "public" ],
          "defaultAclWrite": [],
          "aclCreate": []
        },
        "Schema": {
          "defaultAclRead": [ "public" ],
          "defaultAclWrite": [],
          "aclCreate": []
        }
      },
      "defaultAcls": {
        "defaultAclRead": [ "public" ],
        "defaultAclWrite": [ "creator" ],
        "aclCreate": [ "authenticated" ]
      }
    }

By performing the above steps, you have added a new ACL for the ``User`` type (lines 3-15). Previously, there was not a
separate ACL for the type, and so it used the default ACL, which says that only authenticated users can create objects
of this type. The new ACL says that anyone can create ``User`` objects, and any user can modify their own object. Also,
we have added public permission to run the ``activateAccountIfKeyIsValid`` instance method, which is described below.

Because users can modify their own object, you will need to be careful about what is stored in that object. For
example, it is probably ok if users can deactivate their account, but if user objects can include properties that only
administrators can view or those that empower any user into an administrator status, then do not allow users access to
such properties.

Modify User Schema
~~~~~~~~~~~~~~~~~~

You will have to modify the User type to add the functionality needed to support the registration process.
`Type methods </api/rest-api.html#schema-methods>`_ will be used to generate and send the verification key, and
confirm the key and activate the account.

Creating New User Activation Key
################################

When a user creation request is received by Cordra, Cordra should flag the new account as inactive, generate an
activation key for the account and store it securely, and email that key to the user. To configure Cordra to do that,
you will need to modify the ``User`` schema to add the necessary fields for active status and the activation key. You should
edit the schema and replace the existing JSON with the following:

.. code-block:: json
    :linenos:

    {
      "type": "object",
      "required": [
        "id",
        "username",
        "password",
        "email",
        "isActive"
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
        "username": {
          "type": "string",
          "title": "Username",
          "cordra": {
            "preview": {
              "showInPreview": true,
              "isPrimary": true
            },
            "auth": "username"
          }
        },
        "password": {
          "type": "string",
          "format": "password",
          "title": "Password",
          "cordra": {
            "auth": "password"
          }
        },
        "email": {
          "type": "string",
          "format": "email",
          "title": "Email"
        },
        "publicKey": {
          "type": "object",
          "title": "Public Key",
          "cordra": {
            "auth": "publicKey"
          }
        },
        "requirePasswordChange": {
          "type": "boolean",
          "title": "Require Password Change",
          "description": "If true a new password must be set on next authentication.",
          "cordra": {
            "auth": "requirePasswordChange"
          }
        },
        "isActive": {
          "type": "boolean",
          "title": "Active?",
          "default": false,
          "cordra": {
            "auth": "accountActive"
          }
        },
        "activationKey": {
          "type": "string",
          "format": "password",
          "title": "Activation Key",
          "cordra": {
            "secureProperty": true
          }
        }
      }
    }

By following the above steps, you have added the fields ``email``, ``isActive``, and ``activationKey``,
and made the ``email`` and ``isActive`` field required. Special flags are added using the ``cordra`` property to
indicate that ``isActive`` and ``activationKey`` should be treated differently. The ``accountActive`` property
(line 63) means that Cordra should use this field to indicate whether or not the user account is active. The
``secureProperty`` flag (line 71) means that this field will be hashed and salted before storage and will never be
stored as plain text, so other existing users including the admin cannot view the key.

Next, you will have to modify the ``beforeSchemaValidation`` method on the User object to generate and save the key,
as well as email it to the user. You should replace the default User javascript with the following:

.. code-block:: js
    :linenos:

    exports.beforeSchemaValidation = beforeSchemaValidation;

    var emailConfig = {
        "fromAddress": "admin@example.com",
        "subject": "testing javascript email",
        "textTemplate": "Your activation key is {KEY}.",
        "htmlTemplate": "<html><body><h1>Your activation key is {KEY}.</h1></body></html>"
    };

    function beforeSchemaValidation(obj, context) {
        if (!obj.content.id) obj.content.id = "";
        if (!obj.content.password) obj.content.password = "";
        if (!obj.content.email) obj.content.email = "";
        if (isEmailConfigured() && !isValidEmail(obj.content.email)) {
            throw "Email is invalid."
        }
        if (context.isNew) {
            obj.content.isActive = false;
            obj.content.activationKey = generateRandomString();
            sendKeyEmail(obj.content.email, obj.content.activationKey);
        }
        return obj;
    }

    function generateRandomString() {
        return Math.random().toString(36).substr(2, 15);
    }

    function isValidEmail(email) {
        var re = /\S+@\S+\.\S+/;
        return re.test(email);
    }

    function sendKeyEmail(email, activationKey) {
        if (isEmailConfigured()) {
            var textMessage = emailConfig.textTemplate.replace("{KEY}", activationKey);
            var htmlMessage = emailConfig.htmlTemplate.replace("{KEY}", activationKey);
            sendEmail(email, emailConfig.fromAddress, emailConfig.subject, textMessage, htmlMessage);
        } else {
            print(email + ": " + activationKey);
        }
    }

    function sendEmail(toAddress, fromAddress, subject, textMessage, htmlMessage) {
        // Java types
        var EmailBuilder = Java.type("org.simplejavamail.email.EmailBuilder");
        var MailerBuilder = Java.type("org.simplejavamail.mailer.MailerBuilder");
        var TransportStrategy = Java.type("org.simplejavamail.mailer.config.TransportStrategy");

        // Build email
        var serverConfig = getServerConfig();
        var email = EmailBuilder.startingBlank()
            .to(toAddress)
            .from(fromAddress)
            .withSubject(subject)
            .withHTMLText(htmlMessage)
            .withPlainText(textMessage)
            .buildEmail();

        var mailerBuilder = MailerBuilder
            .withSMTPServer(serverConfig.serverAddress, serverConfig.serverPort, serverConfig.username, serverConfig.password)
            .withSessionTimeout(10000);
        if (serverConfig.enableStartTls) {
            mailerBuilder = mailerBuilder.withTransportStrategy(TransportStrategy.SMTP_TLS);
        } else if (serverConfig.enableStartTls) {
            mailerBuilder = mailerBuilder.withTransportStrategy(TransportStrategy.SMTPS);
        }
        var mailer = mailerBuilder.buildMailer();
        mailer.sendMail(email);
    }

    function getConfigFilePath() {
        var dataDir = java.lang.System.getProperty("cordra.data");
        var filePath = java.nio.file.Paths.get(dataDir).resolve("emailServerConfig.json");
        return filePath;
    }

    function isEmailConfigured() {
        var configFile = getConfigFilePath();
        return java.nio.file.Files.exists(configFile);
    }

    function getServerConfig() {
        var filePath = getConfigFilePath();
        var json = new java.lang.String(java.nio.file.Files.readAllBytes(filePath));
        return JSON.parse(json);
    }

A few things to note here:

* Email configuration for the email to be sent are hardcoded into the JavaScript lines (3-8).
* We are using ``context.isNew`` to make sure we only create an activation key on new object creation (line 17).
* ``beforeSchemaValidation`` runs before the object is stored, so we can modify the key and active flag appropriately
  (lines 18-19).
* Key generation is using a pseudo-random function for the purposes of this tutorial (line 34). For actual use, you
  will want to use a more secure key generation method.
* Email validation in the JavaScript is purposefully minimal (lines 29-32). We will validate the address by sending an
  email to it.
* It is possible to access Java classes in the JavaScript. The ``sendEmail`` function is using classes from the
  jar files we included earlier (lines 476-48).
* Email server config is loaded from local file in the ``loadServerConfig`` function (lines 72-76), so the secrets are
  never network accessible through Cordra. Note that we are also using Java here to read the file.


Confirming a Key
################

Next, you should add a schema instance method for confirming a key by editing the ``User`` javascript and adding the
following to the top:

.. code-block:: js

    var cordraUtil = require('cordraUtil');

    exports.methods = {};
    exports.methods.activateAccountIfKeyIsValid = activateAccountIfKeyIsValid;

You should then add the following JavaScript function to the bottom:

.. code-block:: js

    function activateAccountIfKeyIsValid(object, context) {
        var activationKey = context.params.activationKey;
        if (!activationKey) return false;
        var success = cordraUtil.verifySecret(object, "/activationKey", activationKey);
        if (!success) {
            throw "Could not verify key."
        }
        object.content.isActive = true;
        delete object.content.activationKey;
        return true;
    }

Again, a few things to note:

* We are importing the built-in ``cordraUtil`` javascript module, which gives access to the ``verifySecret`` function
  used in the ``activateAccountIfKeyIsValid`` function. You can read more about the ``cordraUtil`` JavaScript module
  `here </design/business-and-enrichment-rules.html#cordra-module>`_.
* Any instance methods we create will only be available if added to the ``export.methods`` objects.
* Any changes made to the object in an instance method are automatically saved. Here, we are setting ``isActive`` to
  true and removing the activationKey.

Testing It Out
--------------

You should now be able sign up for a user account in Cordra, get an activation key, and use that key to activate the
account. Here are some example curl commands for making the appropriate calls.

Create a new user account::

    curl -k -X POST 'https://localhost:8443/objects/?type=User' -H "Content-Type: application/json" --data @- << END
    {
      "username": "testUser",
      "password": "testPassword",
      "email": "test@example.com"
    }
    END

Activate the user account with the key::

    curl -k -X POST 'https://localhost:8443/call/?objectId=test/a94a8fe5ccb19ba61c4c&method=activateAccountIfKeyIsValid' -H "Content-Type: application/json" --data @- << END
    {
      "activationKey": "XXXXXXXXX"
    }
    END

Note that the ``objectId`` in the activation URI is the id of the User object for this account, not the id of the User
type object. If you are following along, you may need to modify the URI with the id of the User object in your local
Cordra instance.

Admin-created Accounts
----------------------

By following the steps above, you have built a system for allowing users to create and activate their own accounts.
However, in some systems, an administrator creates the accounts for the user, and then the user activates the account
and chooses their password. With a few changes to the code described above, you can support this alternative workflow.

First, you will need to modify the Authorization config to restrict the ability to create User objects. You can do this by simply
removing the ``public`` create ACL for the User type. When complete, your User type ACL should look like this::

    "User": {
      "defaultAclRead": [ "public" ],
      "defaultAclWrite": [ "self" ],
      "aclCreate": [],
      "aclMethods": {
        "instance": {
          "activateAccountIfKeyIsValid": [ "public" ]
        },
        "default": {
          "instance": []
        }
      }
    }

Note that the ``aclCreate`` property is now an empty list. This means that only the ``admin`` user will be able to
create new User objects.

Next, you will have to modify ``beforeSchemaValidation`` to set a temporary password on the newly created user account.
You can do that by changing the method to look like this:

.. code-block:: js
    :linenos:

    function beforeSchemaValidation(obj, context) {
        if (!obj.content.id) obj.content.id = "";
        if (!obj.content.password) obj.content.password = "";
        if (!obj.content.email) obj.content.email = "";
        if (!isValidEmail(obj.content.email)) {
            throw "Email is invalid."
        }
        if (context.isNew) {
            obj.content.isActive = false;
            obj.content.activationKey = generateRandomString();
            obj.content.password = generateRandomString();
            sendKeyEmail(obj.content.email, obj.content.activationKey);
        }
        return obj;
    }

The only change above is that we are setting the password to a random string on line 11.

Finally, the ``activateAccountIfKeyIsValid`` needs to set the new user's password if the activation key is valid. To
do that you can modify the method to look like this:

.. code-block:: js
    :linenos:

    function activateAccountIfKeyIsValid(object, context) {
        var activationKey = context.params.activationKey;
        if (!activationKey) return false;
        var newPassword = context.params.password;
        if (!newPassword || newPassword.length < 8) {
            throw "Password missing or too short. Must be at least 8 characters."
        }
        var success = cordraUtil.verifySecret(object, "/activationKey", activationKey);
        if (!success) {
            throw "Could not verify key."
        }
        object.content.isActive = true;
        object.content.password = newPassword;
        delete object.content.activationKey;
    }

There are a few important changes made above. First, we are checking for the new password in the method context and doing
a small amount of validation (lines 4-7). Object changes within a Type method do not go through validation, so be
sure to do any validation you need in the method. Once the key is verified, you can set the new password on the object
(line 14).

With the above changes in place, you can now test the new account registration workflow.

Because only admin is allowed to create user objects, we must first authenticate to get an access token to use with our
curl command. Use the password you created when starting your Cordra instance.::

    curl -k -X POST 'https://localhost:8443/auth/token' -H "Content-Type: application/json" --data @- << END
    {
        "grant_type": "password",
        "username": "admin",
        "password": "password"
    }
    END


Admin creates a new user account::

    curl -k -X POST 'https://localhost:8443/objects/?type=User' -H "Content-Type: application/json" -H "Authorization: Bearer ADMIN_ACCESS_TOKEN" --data @- << END
    {
      "username": "testUser",
      "email": "test@example.com"
    }
    END


User activates their account with the key and a new password::

    curl -k -X POST 'https://localhost:8443/call/?objectId=test/a94a8fe5ccb19ba61c4c&method=activateAccountIfKeyIsValid' -H "Content-Type: application/json" --data @- << END
    {
      "activationKey": "XXXXXXXXX",
      "password": "newPassword"
    }
    END

Again, be sure to change the ``objectId`` in the URI to match the id of the User object being activated.

Full example
------------

Configurations and code that you will need to follow this description is included in the Cordra download, in the
``extensions/user-registration`` directory. This includes the full User type object and Cordra Authorization config.
It also includes sample web application you can use to test out this functionality. To install the application, create a
directory in your Cordra data directory called ``webapps`` and then copy the ``demo`` directory into the ``webapps``
directory. The demo will now be available at https://localhost:8443/demo.

Additional Thoughts
-------------------

In this tutorial, we have explored a number of topics, including Cordra Type methods, access controls for objects and
methods, and how to use third-party Java libraries in Type methods. This application is just an example, though.
There a few additional things to think about while implementing a secure user registration in a live system. For
example:

* Throttling email sending and account creation.
* Using CAPTCHAs, two-factor authentication, or other alternative account verification methods.
* Expiring activation keys after a certain time.

These topics are important, but are considered out of scope.
