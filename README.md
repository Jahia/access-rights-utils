<a href="https://store.jahia.com/contents/modules-repository/org/jahia/community/modules/access-rights-utils.html">
    <img src="https://www.jahia.com/modules/jahiacom-templates/images/jahia-3x.png" alt="Jahia logo" title="Jahia" align="right" height="60" />
</a>

# Access rights utils

Jahia module that provides utilities related to the access rights

## Scripts to dump / compare / restore the roles

## Prerequisites
You need to install the [Extended Groovy Console](https://store.jahia.com/contents/modules-repository/org/jahia/community/modules/extended-groovy-console.html)

Then, in the console, you will find two prepackaged scripts.

## dumpRoles
Use this script to dump the roles, as defined in the current server, into a JSON file. You can choose to have the file saved on the file system (server side) or uploaded in the JCR.

You can open the file in your prefered JSON client (Firefox has a native support) if you want to view the roles structure, or use it as the input for the other script.

## compareRoles
Use this script to compare the roles defined in your Jahia server with those saved in a JSON file. If some differences are raised, then you can apply them to your Jahia server.

### Typical use cases
#### Compare with the standard roles
Use a JSON file generated on a Jahia server installed in the same version, and with no customization, in order to check the differences with your own server. You might want to reset a native role to its default state, or restore a deleted role.

#### Save your current state before testing some changes
Save your current state, then add/remove some permissions to your roles in the administration (native roles or custom). If you are satisfied with the result, then you can save it into a new file, otherwise you can restore your roles in their previous state.

#### Move your roles structure from one environment to another one
After having validated your new roles structure on a lower environment (UAT), you can save it into a JSON file, and then restore it on your upper environments (preprod, prod).