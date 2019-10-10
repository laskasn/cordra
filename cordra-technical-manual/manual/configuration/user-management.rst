.. _userManagement:

Managing Users and Groups
=========================

Any regular object can be converted into a User or a Group object for purposes of ACLs by
adding a special property ``auth`` to that object schema. See :ref:`here <user-group-auth>` for details
on that property. By converting an object into a User or a Group, Cordra
can enable such objects (once authenticated) to exercise access control over other objects.

Objects that are converted into Users or Groups, as such, can be managed
as regular objects using the API by any user with permission to do so.

Additionally, they can be created and modified in the Cordra UI by authorized users.

JavaScript based rules can be used on User and Group objects, as with any
other object type. This can be useful for tasks like checking password
strength, ensuring that usernames meet certain criteria, or preventing
users or groups from being deleted unless certain conditions are met. See
:ref:`userSchemaJsExample` for and example of how to use JavaScript rules
to perform validation and enrichment tasks.


Adding Users
------------

In the Cordra UI, click "Create" and choose the "User" type.

.. image:: ../_static/users/create_user.png
        :align: center
        :alt: Creating a new User Object

This will open the web interface for editing the new User object.

.. image:: ../_static/users/new_user.png
        :align: center
        :alt: Creating a new User Object

The username and password should be filled in. If the user's password should be changed
after the next authentication, set the "Require Password Change" field to
``true``. Click the save button to save the User object.

Adding Groups
-------------

In the Cordra UI, click "Create" and choose the "Group" type.

.. image:: ../_static/users/create_group.png
        :align: center
        :alt: Creating a new Group Object

This will open the web interface for editing the new Group object.

.. image:: ../_static/users/new_group.png
        :align: center
        :alt: Creating a new Group Object

The Group name should be filled in, but the description field is optional. When ready, click the Add User button
        to add identifiers of user objects.

.. image:: ../_static/users/add_user_button.png
        :align: center
        :alt: Add User button on a Group Object

In the user field, begin by typing the username of the user you would like
to add. As potential matches are found, search results will pop up, allowing
you to select the desired user.

.. image:: ../_static/users/search_for_user.png
        :align: center
        :alt: Searching for a user to add to a group

Once the appropriate user has been found, click on the username to fill in the
user field with the User object identifier.

.. image:: ../_static/users/user_found.png
        :align: center
        :alt: User added to group

Repeat these steps for each user to be added to the group. When all users have
been added, click the "Save" button to save the Group object