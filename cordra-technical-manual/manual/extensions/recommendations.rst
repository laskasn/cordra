.. _objectRecommendations:

Digital Object Recommendations
==============================

The Cordra Recommendations Module is a Java program that can run within a servlet container. The module when properly
configured produces personalized recommendations of Cordra objects for users based on prior ratings associated between
users and Cordra objects. The module is agnostic to the method by which the ratings are
procured, i.e., either procured directly from the user or by some log analysis. The module needs to be seeded with ratings for
each user and some subset of objects. The less the sparsity of the user-object ratings matrix, the higher the quality
of the produced recommendations.

Two recommendation approaches, ``Profile Based`` and ``Similar Profiles Based``, are implemented and both of those rely
on tf-idf values deduced from the index managed by Cordra. This module not only depends on Cordra, but also requires the
underlying indexer be Apache Solr instance that too which is configured in a certain way as discussed below.

Setup
-----

The required sample digital objects, servlet application, and Solr configurations required are included in the Cordra
download distribution in the ``extensions/recommendations`` directory.

Solr Configuration
~~~~~~~~~~~~~~~~~~

This module not only requires Solr to be the configured indexer for Cordra, but also use specific configuration. Solr
configuration files are included with this distribution in the ``extensions/recommendations/solr-cordra-conf`` directory.
These should be copied into the configuration directory ``solr/cordra/conf`` for your Cordra index. For testing purposes,
the follow example Docker command could be used to start the necessary Solr instance::

    docker run -p 8983:8983 -v "${PWD}/extensions/recommendations/solr-cordra-conf:/opt/solr/server/solr/configsets/cordra/" solr:6

You will also need to update your Cordra ``config.json`` file to use Solr for indexing. For example::

    {
      "httpPort": 8080,
      "httpsPort": 8443,
      "listenAddress": "127.0.0.1",
      "index": {
        "module": "solr",
        "options": {
          "baseUri": "http://localhost:8983/solr/cordra"
        }
      }
    }

Cordra Configuration
~~~~~~~~~~~~~~~~~~~~

In order to use this functionality you need to create user objects in your Cordra instance. Cordra objects that can be
rated could be any other type or types of Cordra objects.

You can load the sample objects using the Cordra UI. Sign in into Cordra as ``admin`` and select the ``Admin->Types``
dropdown menu. Click the "Load from file" button. In the dialog that pops up, select the
``recommendations-objects.json`` file you downloaded and check the box to delete existing objects. Click "Load" to
import the objects into Cordra.

To install the recommendations module, copy the ``cordra-recommendations.war`` file in to ``cordra/data/webapps/``.
Next, create a file called ``recommendations-config.json`` in the ``cordra/data/webapps-storage/cordra-recommendations/``
directory. Finally, restart Cordra to enable the module.

Example recommendations-config.json::

    {
        "cordraBaseUri" : "https://localhost:8443/",
        "solrBaseUri" : "http://localhost:8983/solr/cordra",
        "cordraAdminPassword" : "password",
        "enableSimilarProfileMode" : true
    }

If the ``enableSimilarProfileMode`` is set to false, profile-based recommendation approach is chosen instead of
similar-profiles-based approach.

Types of Recommendations
------------------------

In order to produce personalized recommendations, this module needs to know something about user interests or tastes.
The approaches described below infer those user tastes in two specific ways.

Profile Based Recommendations
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In this approach, user taste profiles are computed based on the ratings associated by each user against Cordra objects.

To generate profile based recommendations, first the vectors for the Cordra objects the user has rated are retrieved,
optionally weighted by the ratings. The mean of these vectors is then calculated resulting in a taste vector for the
user. That taste vector is then converted into a query, where each term in the query is boosted by the magnitude of that
term in the taste vector. The results of the query constitute the recommendations.

Similar Profiles Based Recommendations
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Even in this approach, user taste profiles are used as a basis to compute recommendations. The difference, however, is
how the user taste profiles are generated in the first place. In this approach, user taste profiles are computed as the
average taste profile taking into consideration the taste profiles of the most similar users.

When a user rates a Cordra object, the JSON portion of that object is copied into an array in the users profile, stored on the
User object. This results in a user profile being the union of the objects they have rated. These user profiles are
also indexed. To generate recommendations for a user, first the vector for their profile is retrieved and
used to search for other profiles that are most similar to their own. The vectors for the objects that group of similar
users have rated are then retrieved, optionally weighted by the ratings and the mean vector is calculated. This
resulting taste vector represents the taste of the user in question combined with the tastes of users who share similar
taste. This enriched taste vector is used to query for recommendations as before.

Note that this approach only considers the JSON portion of objects (not the payloads) for the profile enrichment.

It is possible to switch between the two approaches, but has some side effects. For example, if you initially have the
property ``enableSimilarProfileMode`` set to false and then later change it to true, that will not modify the existing
user profile objects; only items rated going forward will use this feature.

Example API calls
-----------------

The following examples use the authentication information for the User "paula" included with the sample object. We
are also rating a sample book. If your Cordra instance does not contain the sample data, you will need to change the calls
as needed.

Also, because Cordra comes with a self-signed SSL certificate by default, the example curl commands include the ``-k``
flag to allow curl to ignore the invalid certificate. This flag should not be used in production or against any Cordra
installation with a valid certificate.

Rating a Cordra object
~~~~~~~~~~~~~~~~~~~~~~

Ratings must be integers in the range 1 to 5 inclusive. Unlike star ratings, all ratings here are positive feedback.
If you do not like an item, do not rate it. ::

    curl -k 'https://localhost:8443/cordra-recommendations/rateItem/?itemId=test/61d63ea2fab05688976a&rating=5' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ' -X POST

Liking an object (equivalent to giving the object a rating of 1)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
::

    curl -k 'https://localhost:8443/cordra-recommendations/rateItem/?itemId=test/61d63ea2fab05688976a' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ' -X POST

Deleting a rating
~~~~~~~~~~~~~~~~~
::

    curl -k 'https://localhost:8443/cordra-recommendations/rateItem/?itemId=test/61d63ea2fab05688976a' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ' -X DELETE

Getting recommendations for a user
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
::

    curl -k 'https://localhost:8443/cordra-recommendations/recommendations/' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ'

Getting recommendations for a user boosted by their object ratings
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Boosting by ratings means the individual vectors for rated items are weighted by the users rating prior to being
combined into the taste vector for the user::

    curl -k 'https://localhost:8443/cordra-recommendations/recommendations/?boost=true' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ'

Getting recommendations for a user considering profiles similar to this user
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
::

    curl -k 'https://localhost:8443/cordra-recommendations/recommendations/?mode=similarProfiles' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ'

Finding objects that are similar to a single specified object
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
::

    curl -k 'https://localhost:8443/cordra-recommendations/moreLikeThis/?id=test/61d63ea2fab05688976a' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ'

Finding objects that are similar to a set of specified objects
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
::

    curl -k 'https://localhost:8443/cordra-recommendations/moreLikeThis/?id=test/61d63ea2fab05688976a&id=test/faaac926b64963400080&id=test/80e16ac71eabc8c8e34a' -H 'Authorization: Basic cGF1bGE6cGFzc3dvcmQ'

