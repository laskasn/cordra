We now recommend configuring Handle servers to talk to Cordra on
resolution.

The cordra-client-handle-storage.jar needs to be added to the "lib"
directory of the Handle software distribution.  You will also need to
add the following jar files:

cordra-client.jar
httpclient-4.3.3.jar
httpcore-4.3.2.jar
httpmime-4.3.3.jar
jcl-over-slf4j-1.7.25.jar
log4j-api-2.9.1.jar
log4j-core-2.9.1.jar
log4j-over-slf4j-1.7.25.jar
log4j-slf4j-impl-2.9.1.jar
slf4j-api-1.7.25.jar

Then the following configuration can be added to the Handle server's
config.dct, in the server_config section:

    "storage_type" = "custom"
    "storage_class" = "net.cnri.cordra.handle_storage.CordraClientHandleStorage"
    "storage_config" = {
      "cordra" = {
        "baseUri" = "http://localhost:8080"
        "username" = "admin"
        "password" = "..."
      }
      "javascript" = "@handle.js"
    }

The "storage_config" contains a property "cordra" detailing how the
handle server should contact Cordra, plus properties corresponding to
a Cordra handle minting configuration, as described in the Cordra
Technical Manual.  This should include either a "baseUri" (and
possibly "defaultLinks" and "schemaSpecificLinks") or a "javascript".
If neither is present, "baseUri" from inside "cordra" will be used;
but note that the "cordra" "baseUri" property could specify a
potentially private URI like localhost, while the "baseUri" used for
handle records should generally be public.

If used, the "javascript" property should generally be an at-sign "@"
followed by a filename, which is a file in the Handle server directory
specifying how to transform a Cordra object into a Handle record.  The
JavaScript file needs to define and export a function
"createHandleValue" taking the Cordra object and returning an array of
handle values.  See the Cordra Technical Manual for more information.

Because Cordra treat identifiers as case-sensitive, you will need to
configure the handle server to also be case-sensitive. To do so, set
the "case_sensitive" option in the server_config section to "yes".
