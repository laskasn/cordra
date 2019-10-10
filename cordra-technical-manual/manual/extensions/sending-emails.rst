.. _sendingEmails:

Sending Emails
==============

Cordra can be configured to allow sending emails from :ref:`type-methods`. In order to enable this,
you will need to add the supporting jar libraries to your Cordra instance and then configure the emails you wish to send.

Installation
------------

For this example, we'll use the `Simple Java Mail <http://www.simplejavamail.org>`__ emailing library to send emails.
The jar files needed are included in the Cordra distribution, in the ``extensions/user-registration/lib`` directory.
To install this library, jar files into a directory called ``lib`` in your Cordra data directory. If the ``lib``
directory doesn't exist, create the ``lib`` directory in the Cordra data directory.

You will need to restart the Cordra server once the Java Mail Client library is installed if your Cordra server is
already running.

SMTP Configuration
------------------

Create a file in the ``data`` directory called ``emailServerConfig.json`` with the following contents::

    {
        "serverAddress": "smtp.example.com",
        "serverPort": 587,
        "enableStartTls": true,
        "trustHosts": "smtp.example.com",
        "username": "your-smtp-username",
        "password": "your-smtp-password"
    }

Be sure to replace the example settings with the settings for your actual SMTP server. Finding these settings is outside
of the scope of this document, but most email providers have some way of sending emails using SMTP. You can also use
Amazon's Simple Email Service for testing.

Email Templates
---------------

In the ``data`` directory, create an ``emailTemplates`` directory. In that directory, create files called
``creation.txt`` and ``creation.html``. These files should contain the text and html (respective) that should be sent
in the email.

Sending Emails
--------------

As an example, we'll modify the ``beforeSchemaValidation`` method on the User object to send an email whenever a
new User object is created. Replace the default User schema javascript with the following:

.. code-block:: js
    :linenos:

    exports.beforeSchemaValidation = beforeSchemaValidation;

    var emailConfig = {
        "toAddress": "test@example.com",
        "fromAddress": "admin@example.com",
        "subject": "testing javascript email"
    };

    function beforeSchemaValidation(obj, context) {
        if (context.isNew) {
            sendEmail(emailConfig.toAddress);
        }
        return obj;
    }

    function sendEmail(toAddress) {
        // Java types
        var EmailBuilder = Java.type("org.simplejavamail.email.EmailBuilder");
        var MailerBuilder = Java.type("org.simplejavamail.mailer.MailerBuilder");
        var TransportStrategy = Java.type("org.simplejavamail.mailer.config.TransportStrategy");

        // Build email
        var serverConfig = loadServerConfig();
        var textBody = readFileToString("emailTemplates/creation.txt");
        var htmlBody = readFileToString("emailTemplates/creation.html");
        var email = EmailBuilder.startingBlank()
            .to(toAddress)
            .from(emailConfig.fromAddress)
            .withSubject(emailConfig.subject)
            .withHTMLText(htmlBody)
            .withPlainText(textBody)
            .buildEmail();

        var mailerBuilder = MailerBuilder
            .withSMTPServer(serverConfig.serverAddress, serverConfig.serverPort, serverConfig.username, serverConfig.password)
            .withSessionTimeout(10 * 1000);
        if (serverConfig.enableStartTls) {
            mailerBuilder = mailerBuilder.withTransportStrategy(TransportStrategy.SMTP_TLS);
        } else if (serverConfig.enableStartTls) {
            mailerBuilder = mailerBuilder.withTransportStrategy(TransportStrategy.SMTPS);
        }
        var mailer = mailerBuilder.buildMailer();
        mailer.sendMail(email);
    }

    function loadServerConfig() {
        var json = readFileToString("emailServerConfig.json");
        return JSON.parse(json);
    }

    function readFileToString(filename) {
        var dataDir = java.lang.System.getProperty("cordra.data");
        var filePath = java.nio.file.Paths.get(dataDir).resolve(filename);
        return new java.lang.String(java.nio.file.Files.readAllBytes(filePath));
    }

Next, try creating a new User in Cordra. You should receive an email containing the message specified in the JavaScript
configuration.

The email templates are loaded from the files we created before (lines 24-25). Be sure to modify ``emailConfig`` with
real email addresses to use for sending the email. Email server config is loaded from a local file in the
``loadServerConfig`` function (lines 47-50), so the secrets are never network accessible through Cordra.

We are using ``context.isNew`` to make sure we only create a token on new object creation (line 10). The Java classes
from Simple Mail Client are loaded into the JavaScript (lines 18-20) and then used using the Simple Java Mail API (lines
26-44).
