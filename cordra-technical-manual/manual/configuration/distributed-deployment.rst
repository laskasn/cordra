.. _distributed-deployment:

Deploying Cordra as a Distributed System
========================================

.. contents:: In This Section
    :backlinks: none
    :local:


There are a number of options available for deploying Cordra. You can learn about those options
:ref:`here <configuration_introduction>`. Cordra can be deployed as a distributed system,
with multiple instances of Cordra configured to work together as load-sharing nodes. For deployment in
this manner, Cordra requires the use of those storage and indexing technologies that can be shared by all Cordra nodes
simultaneously. This setup also requires the use of Apache Zookeeper and Apache Kafka to handle coordination between the
Cordra nodes. We describe below the steps needed to deploy Cordra in this distributed fashion.

Outline of Steps
----------------

#. Setup Apache Zookeeper and Apache Kafka.
#. Setup shared storage, indexing, and sessions management.
#. Install and configure Cordra nodes using Apache Tomcat.
#. Configure load balancer for Cordra.
#. Load Cordra configuration information into Zookeeper.

Zookeeper and Kafka Configuration
---------------------------------

The configuration files used by the various Cordra nodes are stored in Zookeeper. Before installing and configuring
Cordra, you will need to set up a Zookeeper cluster followed by a Kafka cluster. Instructions for doing so are
outside the scope of this document, but one setting in Kafka need customization:

In Kafka, the broker ``offsets.retention.minutes`` setting must be longer than the ``log.retention.hours`` topic
setting when compared in the same unit of measure. We recommend setting ``offsets.retention.minutes`` to 11520 if the
default setting of 168 is used for ``log.retention.hours``.

Shared Backend Services
-----------------------

Distributed Cordra requires backend services that can be shared by multiple Cordra nodes. Furthermore, HTTP sessions
must be also be shared. Click on the following links for details on the configuration needed for each option:

* :ref:`storage-configuration`
* :ref:`indexing-configuration`
* :ref:`sessions-configuration`

Apache Tomcat Configuration
---------------------------

Our recommended configuration for setting up Cordra as a distributed system is to use the servlet container Apache
Tomcat, but any servlet container may be used. You can download and use any version of Tomcat; we have tested with
Tomcat v8.5.

If your infrastructure setup includes other applications that also need Tomcat, we recommend separate Tomcat instances
for Cordra and any additional applications.

The Cordra war file, which should be inserted into the Tomcat ``webapps`` directory, is located in the Cordra
distribution at ``sw/webapps-priority/ROOT.war``. It can be renamed to ``cordra.war``, for instance, if desired.

The main Tomcat configuration file is ``conf/server.xml``. Summarizing the changes needed from the default configuration:

#. Change the shutdown port attribute of the outermost Server element,
   so that each Tomcat instance on one machine has a different shutdown port.
#. Set an address and port on the HTTP Connector element.
#. Configure an HTTPS Connector if needed.
#. Delete the AJP Connector (it is not needed).
#. Add a Cluster element to the Engine element, for session replication. See
   `Session Replication <#session-replication>`__.  If preferred, instead of
   configuring session replication, the Cordra instances can be placed behind
   a load balancer with "session affinity" or "sticky sessions", or a
   distributed store for sessions can be configured in Cordra's configuration
   as described in :ref:`sessions-configuration`.

One other piece of configuration is needed to ensure that Cordra knows where to
produce log files. This is done using Java system properties, which can be set
in Tomcat via an environment variable ``CATALINA_OPTS``. This setting can be set
using the file ``bin/setenv.sh`` which can be created as a sibling of
``bin/catalina.sh``.  Ensure that ``bin/setenv.sh`` has the following contents::

   CATALINA_OPTS="-Dcordra.data=/path/to/cordra/datadir ${CATALINA_OPTS}"

``CATALINA_OPTS`` can also be used to specify memory configuration, such
as::

   CATALINA_OPTS="-Xmx2G -Dcordra.data=/path/to/cordra/datadir ${CATALINA_OPTS}"

Logging can be configured with a ``log4j2.xml`` file in the ``cordra.data``
directory.

Cordra log files can be forwarded to a separate indexing system as discussed
here :ref:`logs-management`.


.. _session-replication:

Session Replication
~~~~~~~~~~~~~~~~~~~

Tomcat session replication may be configured for each distributed set of Tomcat servers. This allows all the
Tomcat instances to share authentication tokens.

If preferred, instead of configuring session replication, the Cordra instances can be placed behind a load balancer
with "session affinity" or "sticky sessions".

Another alternative is to configure Cordra itself to be responsible for sessions stored in a distributed store such as
MongoDB.  For more information see :ref:`sessions-configuration`.

If configuring Tomcat for session replication, here is a sample of the relevant session replication configuration,
inside the Engine element in Tomcat's server.xml::

    <Cluster className="org.apache.catalina.ha.tcp.SimpleTcpCluster" channelStartOptions="3" channelSendOptions="6">
        <Manager className="org.apache.catalina.ha.session.DeltaManager"/>
        <Channel className="org.apache.catalina.tribes.group.GroupChannel">
            <Receiver className="org.apache.catalina.tribes.transport.nio.NioReceiver"
                    address="10.5.0.106"
                    port="6000"
                        />
            <Sender className="org.apache.catalina.tribes.transport.ReplicationTransmitter">
                <Transport className="org.apache.catalina.tribes.transport.nio.PooledParallelSender"/>
            </Sender>
            <Interceptor className="org.apache.catalina.tribes.group.interceptors.TcpPingInterceptor"/>
            <Interceptor className="org.apache.catalina.tribes.group.interceptors.TcpFailureDetector"/>
            <Interceptor className="org.apache.catalina.tribes.group.interceptors.MessageDispatchInterceptor"/>
            <Interceptor className="org.apache.catalina.tribes.group.interceptors.StaticMembershipInterceptor">
                    <!--
                    <Member className="org.apache.catalina.tribes.membership.StaticMember"
                        host="10.5.0.106"
                        port="6000"
                    />
                    -->
                    <Member className="org.apache.catalina.tribes.membership.StaticMember"
                        host="10.5.0.104"
                        port="6000"
                    />

                    <Member className="org.apache.catalina.tribes.membership.StaticMember"
                        host="10.5.0.105"
                        port="6000"
                    />

            </Interceptor>
        </Channel>
        <Valve className="org.apache.catalina.ha.tcp.ReplicationValve"/>
        <ClusterListener className="org.apache.catalina.ha.session.ClusterSessionListener"/>
    </Cluster>

Note that in Tomcat 8.0, ``MessageDispatchInterceptor`` should be replaced with ``MessageDispatch15Interceptor``.

Most of the above configuration snippet is boilerplate. The only things to change are the address/port attributes of
the Receiver element, which correspond to the server being configured, and the host/port attributes of the
Member elements, which correspond to the other servers in the group. Note that the server being configured should not
be included in a Member element (and it is commented out in the above example).

Application Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~

For Cordra and each additional application, WEB-INF/web.xml must be edited to insert the appropriate Zookeeper
connection string, which indicates to Zookeeper client how to establish connection with the configured Zookeeper cluster.
The rest of the configuration will be obtained from Zookeeper.

Example from Cordra web.xml::

    <context-param>
        <param-name>zookeeperConnectionString</param-name>
        <param-value>10.5.0.101:2181,10.5.0.102:2181,10.5.0.103:2181/cordra</param-value>
    </context-param>

Load Balancer Configuration
---------------------------

You will need to set up a load balancer in front of the Tomcat servers hosting Cordra. If you are using Amazon EC2 to
host your servers, create "Classic" Load Balancer for Cordra. Applications should be configured to talk to the Cordra
load balancer, instead of talking to any given Cordra server directly.

If you have elected not to configure session replication in Tomcat, it will be necessary to configure the load balancer
to use "session affinity" or "sticky sessions", so that a client which was initially redirected by the load balancer to a
particular Cordra node will continue to be forwarded to the same node for subsequent requests (and therefore the
session information provided by the client continues to be accepted by the (only) Cordra node which knows that information).

Cordra Configuration
--------------------

The Cordra config.json file should be stored as znode (on Zookeeper)
``/cordra/config.json``. If used, the private key should be stored as znode
``/cordra/privatekey``, or, because the Zookeeper zkCli.sh script does not
provide an easy way to work with binary files, it can be stored in a
Base64-encoded form as znode ``/cordra/privatekey.base64``.) If a Cordra
``repoInit.json`` file is used, it should be stored as znode
``/cordra/repoInit.json``.

Zookeeper includes a tool called zkCli.sh which can be used to install these files::

   bin/zkCli.sh create /cordra ""
   bin/zkCli.sh create /cordra/config.json "$(cat cordra/config.json)"
   bin/zkCli.sh create /cordra/repoInit.json "$(cat cordra/repoInit.json)"

An individual Cordra instance can be given a different znode for configuration.
This is done using the ``configName`` context-param in web.xml::

    <context-param>
        <param-name>configName</param-name>
        <param-value>read-only-config.json</param-value>
    </context-param>

The configName is interpreted relative to the
``zookeeperConnectionString``, so this example would be znode
``/cordra/read-only-config.json``.

..  COMMENTED OUT A read-only Cordra instance that is part of a primary region should be
    configured with ``"isReadOnly":true`` and with no ``replicationConsumer``.
    A read-only Cordra instance that is part of a secondary region should
    be configured with ``"isReadOnly":true`` and with a configured
    ``replicationConsumer``.

.. COMMENTED OUT Properties in Cordra's config.json configure cross-region replication
    and a transaction reprocessing queue. Both of these are implemented by
    specifying Kafka bootstrap servers. The ``replicationProducers`` are all
    the Kafka services, including the one in the local region; the
    ``replicationConsumer`` and the reprocessing queue are both the Kafka
    service in the local region. Finally each region must be given an identifier
    ``cordraClusterId`` (a number, or the AWS region name, would work), and
    all regions except one should be marked ``"isReadOnly": true``.

.. COMMENTED OUT Each Kafka service will have a topic called ``CordraReprocessing`` and
    multiple topics called ``CordraReplication-CLUSTERID``, one for each
    cordraClusterId, including the local one as well as each of the remote
    ones. To obtain performance benefits of concurrent processing, each
    of these topics (both ``CordraReprocessing`` and the various
    ``CordraReplication-*``) should be created with as many partitions as
    there are Cordra servers in the Kafka's region.

Cordra keeps track of user requests that require re-processing as part of its fault tolerance logic. A transaction
reprocessing queue is managed with the help the configured Kafka service tracked with the topic name
``CordraReprocessing``. To boost performance benefits, you can enable multiple Cordra nodes to be able to process the
transactions on the queue concurrently; for that, this Kafka topic should be created on Kafka with as many partitions as
there are Cordra servers.

Sample Cordra ``config.json``::

    {
        "isReadOnly": false,
        "index" : {
            "module" : "solr",
            "options" : {
                "zkHosts" : "10.7.1.101:2181,10.7.2.101:2181,10.7.3.101:2181/solr"
            }
        },
        "storage" : {
            "module" : "mongodb",
            "options" : {
                "connectionUri" : "mongodb://10.7.1.102:27017,10.7.2.102:27017,10.7.3.102:27017/replicaSet=rs0&w=majority&journal=true&wtimeoutMS=30000&readConcernLevel=linearizable"
            }
        },
        "reprocessingQueue" : {
            "type": "kafka",
            "kafkaBootstrapServers": "10.7.1.101:9092,10.7.2.101:9092,10.7.3.101:9092"
        }
    }

.. COMMENTED OUT    {
        "isReadOnly": false,
        "cordraClusterId": "1",
        "index" : {
            "module" : "solr",
            "options" : {
                "zkHosts" : "10.7.1.101:2181,10.7.2.101:2181,10.7.3.101:2181/solr"
            }
        },
        "storage" : {
            "module" : "mongodb",
            "options" : {
                "connectionUri" : "mongodb://10.7.1.102:27017,10.7.2.102:27017,10.7.3.102:27017/replicaSet=rs0&w=majority&journal=true&wtimeoutMS=30000&readConcernLevel=linearizable"
            }
        },
        "replicationProducers": [
            {
                "type": "kafka",
                "kafkaBootstrapServers": "10.7.1.101:9092,10.7.2.101:9092,10.7.3.101:9092"
            },
            {
                "type": "kafka",
                "kafkaBootstrapServers": "54.212.229.94:9092,54.202.6.107:9092,54.201.173.190:9092"
            }
        ],
        "replicationConsumer" : {
            "type": "kafka",
            "kafkaBootstrapServers": "10.7.1.101:9092,10.7.2.101:9092,10.7.3.101:9092"
        },
        "reprocessingQueue" : {
            "type": "kafka",
            "kafkaBootstrapServers": "10.7.1.101:9092,10.7.2.101:9092,10.7.3.101:9092"
        }
    }

Note that Cordra's ``config.json`` no longer needs Cordra's listen address or port information, as that is now part of
Tomcat configuration.
