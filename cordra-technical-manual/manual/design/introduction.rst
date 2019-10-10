.. _design_introduction:

Introduction
============

Cordra is a highly configurable software system for managing information as digital objects. One primary way to use Cordra
is to customize it to meet your feature requirements, and then directly expose it to your API users.

Cordra offers a :ref:`unified programmable interface <api_introduction>` across your :ref:`storage system <storage-configuration>`
and :ref:`indexing system <indexing-configuration>`, and manages information logically as digital objects. Digital objects are
typed, and Cordra enforces :ref:`schemas <types>` on user-supplied JSON information. It applies different validation and
enrichment rules at various stages of a digital object lifecycle using :ref:`hooks <javascript-lifecycle-hooks>`.
In addition to JSON, it stores and indexes arbitrary information in the form of :ref:`payloads <payloads>`. It allots
:ref:`identifiers <identifiers>` to digital objects that can be resolved by consumers to learn the location of digital
objects wherever the Cordra instances that manage those digital objects are deployed. It authenticates users and
enforces access controls via its :ref:`ACL component <aa>` that manages :ref:`users as well as groups <userManagement>`
as digital objects. You may want to allow your potential users to :ref:`self-register <user_registration>` their accounts
in Cordra, or want to provide them :ref:`personalized recommendations <objectRecommendations>`.

Cordra allows users to take :ref:`snapshots <objectVersioning>` of digital objects. It also allows :ref:`hashing <objectHashing>`
that could be used for :ref:`linking <objectLinking>` between digital objects, not just in a linear order, but also as
graphs of objects of defined types.

If you prefer to use Cordra for managing complex applications, and the combination of provided APIs, configurations, and
lifecycle hooks are not adequate for enabling desired capabilities, :ref:`additional operations <type-methods>` can be
added on the server-side environment so your :ref:`clients <client_introduction>` can remain simple.

The aforementioned capabilities can be configured to the most part using :ref:`types <types>`, the :ref:`Design object <design-object>`,
and startup files. By default, Cordra is configured to behave as a :ref:`Document Repository <document_repository>`,
but it can be customized to support complex application needs. A glimpse of its customization power is shown in this
:ref:`Medical Records Application <emr>`. In some cases, instead of directly exposing Cordra's APIs to your users, you
may desire to deploy applications that rely on Cordra, a simple example of which is a :ref:`OAI-PMH API <oai_pmh>` that
librarians rely on for gathering metadata from managed information.

Cordra can be deployed on a :ref:`single machine <single_instance_deployment>` or as a
:ref:`distributed system <distributed-deployment>`. In a distributed system mode, off-the-shelf load balancers can be
used to redirect traffic to the cluster of Cordra nodes. Access and error logs can also be coherently
:ref:`managed <logs-management>`.

Management of complex infrastructure requires tools and tutorials related to
:ref:`keys management <https_configuration>`, :ref:`distributed sessions management <sessions-configuration>`,
:ref:`logs management <logs-management>`, :ref:`user management <userManagement>`, :ref:`administrative interface <adminUI>`,
:ref:`storage import and export <import_export>`, and :ref:`environment migration <migration>`.

Cordra software is :ref:`continuously maintained <release_notes>` and is offered to the public in
`open source form <https://www.cordra.org>`__.
