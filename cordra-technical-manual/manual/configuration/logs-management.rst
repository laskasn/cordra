.. _logs-management:

Managing Cordra Logs
====================

Access Logs
-----------

If Cordra is run within Tomcat, then Tomcat's default settings enable Tomcat to produce access logs. If Cordra is run as a
standalone process, then Jetty is used for running Cordra. By default, Jetty does not produce access logs. The following
sample configuration can be stored in ``data/jetty.xml`` for Jetty to produce access logs, where ``data`` is the Cordra's
data directory::

   <?xml version="1.0"?>
   <!DOCTYPE Configure PUBLIC "-//Jetty//Configure//EN" "http://www.eclipse.org/jetty/configure_9_3.dtd">

   <!-- =============================================================== -->
   <!-- Configure the Jetty Request Log                                 -->
   <!-- =============================================================== -->
   <Configure id="Server" class="org.eclipse.jetty.server.Server">

     <!-- =========================================================== -->
     <!-- Configure Request Log for Server                            -->
     <!-- (Use RequestLogHandler for a context specific RequestLog    -->
     <!-- =========================================================== -->
     <Set name="RequestLog">
       <New id="RequestLog" class="org.eclipse.jetty.server.AsyncNCSARequestLog">
         <Set name="filename"><SystemProperty name="cordra.data"/>/logs/jetty-request.log-yyyy_MM_dd</Set>
         <Set name="filenameDateFormat">yyyyMM</Set>
         <Set name="retainDays">0</Set>
         <Set name="append">true</Set>
         <Set name="extended">false</Set>
         <Set name="logCookies">false</Set>
         <Set name="LogTimeZone">GMT</Set>
         <Set name="LogLatency">false</Set>
       </New>
     </Set>
   </Configure>

Error Logs
----------

Cordra logs errors by default, and those entries are written to files that begin with ``error.log`` in the directory
``data/logs``. One file per month will be produced, and the actual file name will reflect the year and month, e.g.,
error.log-201510, for the error log that corresponds to the entries for October 2015.


Index of Log
------------

It may be be useful to send the logged entries from access and error logs to an indexing service for retrieval and analysis.
You may choose to use Elasticsearch as that indexing service. You will also need to install and configure two tools, Logstash
and Filebeat to transform and register the logs with Elasticsearch.

The following instructions describe how to configure Logstash and Filebeat to register error logs with an Elasticsearch
indexer that is running on port 9200 on localhost.


Install and Configure Logstash
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Install Logstash using the `instructions on the Logstash website <https://www.elastic.co/guide/en/logstash/5.6/installing-logstash.html>`__.

#. Use the |logstash-plugin command|__ to install the following plugins:

   .. |logstash-plugin command| replace:: ``logstash-plugin`` command
   __ https://www.elastic.co/guide/en/logstash/5.6/working-with-plugins.html

   * ``logstash-input-beats``
   * ``logstash-output-elasticsearch``
   * ``logstash-filter-date``
   * ``logstash-filter-grok``
   * ``logstash-filter-mutate``

#. Create a file called ``cordra.conf`` in the Logstash configuration directory. Copy the
   following into that file::

    input {
        beats {
            port => 5044
        }
    }

    filter {
        if [fields][app_source] == "cordra_error" {
            grok {
              match => [ "message", "%{DATA:[@metadata][timestamp]}\s*\[%{DATA:thread}\]\s*%{LOGLEVEL:loglevel}\s*%{DATA:logger}\s*-\s* %{GREEDYDATA:msg}" ]
            }
            date { match => [ "[@metadata][timestamp]", "yyyy-MM-dd HH:mm:ss.SSSZ" ] }
            mutate { remove_field => ["message"] }
        }
    }

    output {
        elasticsearch {
            hosts => ["localhost:9200"]
            # Use the following format for AWS hosted Elasticsearch
            # hosts => ["https://amazon-hosted-elasticsearch.example.com:443"]
            manage_template => false
            index => "%{[@metadata][beat]}-%{+YYYY.MM.dd}"
            document_type => "%{[@metadata][type]}"
        }
    }

   Be sure to edit the Elasticsearch host as necessary.

#. Start the Logstash service.

Install and Configure Filebeat
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Install Filebeat using the `instructions on the Filebeat website <https://www.elastic.co/guide/en/beats/filebeat/5.6/filebeat-installation.html>`__.
#. `Manually load the Filebeat index template into Elasticsearch <https://www.elastic.co/guide/en/beats/filebeat/5.6/filebeat-template.html#load-template-manually.>`__.
#. Replace the default filebeat.yml with the following::

    #================== Filebeat prospectors ==================

    filebeat.prospectors:

    - input_type: log

    paths:
        - /path/to/cordra/logs/error.log*
    exclude_files: ['\.gz$']

    fields:
        app_source: cordra_error

    # Java stack traces
    #multiline.pattern: '^[[:space:]]+|^Caused by:'
    multiline.pattern: '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
    multiline.negate: true
    multiline.match: after

    #====================== General ====================

    name: cordra

    #====================== Outputs ====================

    output.logstash:
        hosts: ["127.0.0.1:5044"]

   Edit Logstash host and log file location as necessary.
#. Start the Filebeat service.
