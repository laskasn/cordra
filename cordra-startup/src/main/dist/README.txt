README
======

In this README, we describe how to start and stop single instance Cordra
on *nix environments. The provided instructions can be used as templates
starting and stopping on Cordra on Windows platforms.

That said, please refer to the Cordra website for detailed deployment instructions
including instructions to setup a distributed set of load-sharing Cordra instances.
Those instructions are also made available for offline use in the included
Cordra Technical Manual.


Configure and Run
-----------------

Before starting Cordra for the first time, the admin password needs to
be set in a "repoInit.json" file under the "data" directory.  This
file should also contain the desired handle prefix; if the prefix is
omitted the prefix "test" will be used to create initial schema objects.

Example data/repoInit.json:

{
  "adminPassword": "changeit",
  "prefix": "20.5000.123.TEST"
}

Once data/repoInit.json is in place, you can start Cordra with the
"startup" script, and stop it with the "shutdown" script.

When Cordra starts, the admin password will be securely stored in the
system and the repoInit.json file will be deleted. The admin password
and prefix can then be changed using the admin web interface.

Cordra should now be available by visiting http://localhost:8080/ in a browser.
You can sign in as admin in order to further configure your server, if desired.
Only users authenticated as "admin" can use the admin interface. Click
the "Sign In" button and sign in as "admin".