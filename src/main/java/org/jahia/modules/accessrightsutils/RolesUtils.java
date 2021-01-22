package org.jahia.modules.accessrightsutils;

import org.apache.commons.collections.Closure;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRFileNode;
import org.jahia.services.query.QueryWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RolesUtils {

    private static final Logger logger = LoggerFactory.getLogger(RolesUtils.class);
    private static final String WORKSPACE = Constants.EDIT_WORKSPACE;
    private static final String STMT = "SELECT * FROM [jnt:role] WHERE ISDESCENDANTNODE('/roles')";

    public List<String> dumpRolesToFile() throws RepositoryException {
        return dumpRolesToFile("filesystem");
    }

    public List<String> dumpRolesToFile(String location) throws RepositoryException {

        final boolean[] interrupted = {false};

        final ScriptLogger log = new ScriptLogger(logger);

        final JSONObject report = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, new JCRCallback<JSONObject>() {
            @Override
            public JSONObject doInJCR(JCRSessionWrapper session) throws RepositoryException {
                final QueryWrapper query = session.getWorkspace().getQueryManager().createQuery(STMT, Query.JCR_SQL2);
                final JCRNodeIteratorWrapper nodes = query.execute().getNodes();
                final JSONObject report = new JSONObject();
                while (nodes.hasNext()) {
                    if (System.getProperty("interruptScript") != null) {
                        log.info("Script interrupted");
                        System.clearProperty("interruptScript");
                        interrupted[0] = true;
                        return null;
                    }
                    final Node role = nodes.nextNode();
                    final JSONObject roleJSON = new JSONObject();
                    try {
                        report.put(role.getName(), roleJSON);
                        roleJSON.put("path", role.getPath());
                        if (role.getParent().isNodeType(Constants.JAHIANT_ROLE))
                            roleJSON.put("parentRole", role.getParent().getName());
                        dumpPropertyValue(roleJSON, role, "j:permissionNames", true);
                        dumpPropertyValue(roleJSON, role, "j:roleGroup");
                        dumpPropertyValue(roleJSON, role, "j:hidden");
                        dumpPropertyValue(roleJSON, role, "j:nodeTypes", true);
                        dumpPropertyValue(roleJSON, role, "j:privilegedAccess");
                        final NodeIterator it = role.getNodes();
                        while (it.hasNext()) {
                            final Node extPerm = (Node) it.next();
                            if (!extPerm.isNodeType("jnt:externalPermissions")) continue;
                            final JSONObject extPermRootJSON;
                            if (roleJSON.has("externalPermissions")) {
                                extPermRootJSON = roleJSON.getJSONObject("externalPermissions");
                            } else {
                                extPermRootJSON = new JSONObject();
                                roleJSON.put("externalPermissions", extPermRootJSON);
                            }
                            final JSONObject extPermJSON = new JSONObject();
                            extPermRootJSON.put(extPerm.getName(), extPermJSON);
                            dumpPropertyValue(extPermJSON, extPerm, "j:path");
                            dumpPropertyValue(extPermJSON, extPerm, "j:permissionNames", true);
                        }
                    } catch (JSONException e) {
                        log.error("", e);
                    }
                }

                return report;
            }

            void dumpPropertyValue(JSONObject json, Node node, String pName) throws RepositoryException, JSONException {
                dumpPropertyValue(json, node, pName, false);
            }

            void dumpPropertyValue(JSONObject json, Node node, String pName, boolean multiple) throws RepositoryException, JSONException {
                if (!node.hasProperty(pName)) return;
                if (!multiple) {
                    json.put(pName, node.getProperty(pName).getString());
                    return;
                }
                for (Value v : node.getProperty(pName).getValues()) {
                    json.accumulate(pName, v.getString());
                }

            }
        });

        if (!interrupted[0]) {
            final String target = location == null ? StringUtils.EMPTY : location.trim().toLowerCase();
            switch (target) {
                case "filesystem":
                    writeDumpOnTheFilesystem(report, log);
                    break;
                case "jcr":
                    writeDumpInTheJCR(report, log);
                    break;
                default:
                    log.error("Unexpected location to write the dump: " + location);
            }
        }

        return log.getBuffer();
    }

    private void writeDumpOnTheFilesystem(JSONObject report, ScriptLogger log) {
        final File outputDir = new File(System.getProperty("java.io.tmpdir"), "roles-reports");
        final boolean folderCreated = outputDir.exists() || outputDir.mkdirs();
        if (folderCreated && outputDir.canWrite()) {
            final String filename = String.format("roles-report-%s.json",
                    FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(System.currentTimeMillis()));
            final File jsonFile = new File(outputDir, filename);
            try {
                jsonFile.createNewFile();
                FileUtils.writeStringToFile(jsonFile, report.toString(), StandardCharsets.UTF_8);
                log.info("Written the report in " + jsonFile.getPath());
            } catch (IOException e) {
                log.error("Impossible to write the report", e);
            }
        } else {
            log.error("Impossible to write the folder " + outputDir.getPath());
        }
    }

    private void writeDumpInTheJCR(JSONObject report, ScriptLogger log) throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, new JCRCallback<Void>() {
            @Override
            public Void doInJCR(JCRSessionWrapper session) throws RepositoryException {
                final JCRNodeWrapper filesFolder = session.getNode("/sites/systemsite/files");
                final JCRNodeWrapper outputDir = filesFolder.hasNode("roles-reports") ?
                        filesFolder.getNode("roles-reports") :
                        filesFolder.addNode("roles-report", Constants.JAHIANT_FOLDER);
                if (!outputDir.isNodeType(Constants.JAHIANT_FOLDER)) {
                    log.error(String.format("Impossible to write the folder %s of type %s", outputDir.getPath(), outputDir.getPrimaryNodeTypeName()));
                    return null;
                }
                final String filename = String.format("roles-report-%s.json",
                        FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(System.currentTimeMillis()));
                final JCRNodeWrapper reportNode = outputDir.uploadFile(filename, IOUtils.toInputStream(report.toString()), "application/json");
                session.save();
                log.info("Written the report in " + reportNode.getPath());
                return null;
            }
        });
    }

    public List<String> runRolesComparison(String dumpedRolesPath, String localOnlyRolesToDelete, String missingRolesToCreate, String resetDifferentRoles) throws RepositoryException {

        final boolean[] interrupted = {false};

        final ScriptLogger log = new ScriptLogger(logger);

        // not handled case: non unique role key (such as /roles/duplicate and /roles/editor/duplicate )
        final Set<String> srcOnlyRoles = new HashSet<>();
        final Set<String> localOnlyRoles = new HashSet<>();
        final Set<String> identicalRoles = new HashSet<>();
        final Map<String, List<String>> differentRoles = new HashMap<>();

        String jsonString = null;
        if (dumpedRolesPath.startsWith("jcr:")) {
            final String reportNodePath = dumpedRolesPath.substring(4);
            jsonString = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, new JCRCallback<String>() {
                @Override
                public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    final JCRNodeWrapper node = session.getNode(reportNodePath);
                    if (node.isNodeType(Constants.JAHIANT_FILE)) {
                        final JCRFileNode file = (JCRFileNode) node;
                        return file.getFileContent().getText();
                    }
                    return null;
                }
            });
        } else {
            final File jsonFile = new File(dumpedRolesPath);
            if (!jsonFile.exists()) {
                log.error("The specified file does not exist");
                return log.getBuffer();
            }
            try {
                jsonString = FileUtils.readFileToString(jsonFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("", e);
            }
        }
        if (jsonString == null) {
            log.error("Impossible to read the file");
            return log.getBuffer();
        }

        final JSONObject report;
        try {
            report = new JSONObject(jsonString);
        } catch (JSONException jsonException) {
            if (!log.isDebugEnabled())
                log.error("The specified file is not a valid JSON file");
            else
                log.debug("The specified file is not a valid JSON file", jsonException);
            return log.getBuffer();
        }

        final Iterator it = report.keys();
        while (it.hasNext()) {
            final String key = (String) it.next();
            srcOnlyRoles.add(key);
        }

        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, new JCRCallback<Void>() {
            @Override
            public Void doInJCR(JCRSessionWrapper session) throws RepositoryException {
                final QueryWrapper query = session.getWorkspace().getQueryManager().createQuery(STMT, Query.JCR_SQL2);
                final JCRNodeIteratorWrapper nodes = query.execute().getNodes();
                while (nodes.hasNext()) {
                    if (System.getProperty("interruptScript") != null) {
                        log.info("Script interrupted");
                        System.clearProperty("interruptScript");
                        interrupted[0] = true;
                        break;
                    }
                    final Node role = nodes.nextNode();
                    final String roleName = role.getName();
                    if (!srcOnlyRoles.remove(roleName)) {
                        localOnlyRoles.add(roleName);
                        continue;
                    }
                    final List<String> differences;
                    try {
                        differences = compareRoles(role, report.getJSONObject(roleName));
                        if (CollectionUtils.isEmpty(differences))
                            identicalRoles.add(roleName);
                        else
                            differentRoles.put(roleName, differences);
                    } catch (JSONException e) {
                        log.error("", e);
                    }
                }
                return null;
            }

            List<String> compareRoles(Node locRole, JSONObject refRole) throws RepositoryException, JSONException {
                final List<String> differences = new ArrayList<>();

                final String locParent = locRole.getParent().isNodeType(Constants.JAHIANT_ROLE) ? locRole.getParent().getName() : null;
                final String refParent = refRole.has("parentRole") ? refRole.getString("parentRole") : null;
                if (!StringUtils.equals(locParent, refParent)) {
                    differences.add(String.format("Different parent: [local=%s] , [reference=%s]", locParent, refParent));
                }

                compareProperty(locRole, refRole, "j:roleGroup", "role group", differences);
                compareProperty(locRole, refRole, "j:hidden", "hidden state", differences);
                compareProperty(locRole, refRole, "j:privilegedAccess", "privilegedAccess state", differences);
                compareMultipleProperty(locRole, refRole, "j:nodeTypes", "Node type", differences);
                compareMultipleProperty(locRole, refRole, "j:permissionNames", "Permission", differences);

                compareExternalPermissions(locRole, refRole, differences);

                return differences;
            }

            void compareProperty(Node locRole, JSONObject refRole, String propertyName, String label, List<String> differences) throws RepositoryException, JSONException {
                final String locValue = locRole.hasProperty(propertyName) ? locRole.getProperty(propertyName).getString() : null;
                final String refValue = refRole.has(propertyName) ? refRole.getString(propertyName) : null;
                if (!StringUtils.equals(locValue, refValue)) {
                    differences.add(String.format("Different %s: [local=%s] , [reference=%s]", label, locValue, refValue));
                }
            }

            void compareMultipleProperty(Node locObject, JSONObject refObject, String propertyName, String label, List<String> differences) throws RepositoryException, JSONException {
                final List<String> locPropValues = new ArrayList<>();
                if (locObject.hasProperty(propertyName)) {
                    for (Value val : locObject.getProperty(propertyName).getValues())
                        locPropValues.add(val.getString());
                }
                final List<String> refPropValues = new ArrayList<>();
                if (refObject.has(propertyName)) {
                    final Object nodeTypes = refObject.get(propertyName);
                    if (nodeTypes instanceof String)
                        refPropValues.add((String) nodeTypes);
                    else if (nodeTypes instanceof JSONArray) {
                        final JSONArray array = refObject.getJSONArray(propertyName);
                        for (int i = 0; i < array.length(); i++)
                            refPropValues.add(array.getString(i));
                    } else {
                        log.error("Unexpected type: " + nodeTypes.getClass());
                    }
                }
                CollectionUtils.forAllDo(CollectionUtils.disjunction(locPropValues, refPropValues), new Closure() {
                    @Override
                    public void execute(Object input) {
                        if (StringUtils.isBlank((String) input)) return;
                        differences.add(String.format("%s configured only on the %s role: %s",
                                label,
                                locPropValues.contains(input) ? "local" : "reference",
                                input));
                    }
                });
            }

            void compareExternalPermissions(Node locRole, JSONObject refRole, List<String> differences) throws JSONException, RepositoryException {
                final Set<String> refOnlyContext = new HashSet<>();
                final Set<String> localOnlyContext = new HashSet<>();

                JSONObject extPermRootJSON = null;
                if (refRole.has("externalPermissions")) {
                    extPermRootJSON = refRole.getJSONObject("externalPermissions");
                    final Iterator it = extPermRootJSON.keys();
                    while (it.hasNext()) {
                        final String key = (String) it.next();
                        refOnlyContext.add(key);
                    }
                }
                final NodeIterator it = locRole.getNodes();
                while (it.hasNext()) {
                    final Node extPerm = (Node) it.next();
                    if (!extPerm.isNodeType("jnt:externalPermissions")) continue;
                    final String extPermName = extPerm.getName();
                    if (!refOnlyContext.remove(extPermName)) {
                        localOnlyContext.add(extPermName);
                        continue;
                    }
                    final JSONObject extPermJSON = extPermRootJSON.getJSONObject(extPermName);
                    compareProperty(extPerm, extPermJSON, "j:path", "context node path", differences);
                    final String jPath = extPermJSON.has("j:path") ? extPermJSON.getString("j:path") : String.format("[unexpected missing j:path property on node %s]", extPermName);
                    compareMultipleProperty(extPerm, extPermJSON, "j:permissionNames", String.format("Permission (tested on %s)", jPath), differences);
                }
                for (String ctx : refOnlyContext) {
                    differences.add(String.format("External permissions declared only on the reference node: %s", ctx));
                }
                for (String ctx : localOnlyContext) {
                    differences.add(String.format("External permissions declared only on the local node: %s", ctx));
                }
            }
        });

        log.info(String.format("Roles only in the source file: %s", srcOnlyRoles));
        createMissingRoles(missingRolesToCreate, srcOnlyRoles, report, log, interrupted);
        log.info(String.format("Roles only in the local server: %s", localOnlyRoles));
        deleteLocalOnlyRoles(localOnlyRolesToDelete, localOnlyRoles, log, interrupted);
        log.info(String.format("Roles with no differences: %s", identicalRoles));
        log.info("Roles with differences:");
        for (String role : differentRoles.keySet()) {
            log.info(String.format(" * %s", role));
            final List<String> diffs = new ArrayList<>(differentRoles.get(role));
            diffs.sort(Comparator.naturalOrder());
            for (String diff : diffs)
                log.info(String.format("    - %s", diff));
        }
        resetRoles(resetDifferentRoles, differentRoles.keySet(), report, log, interrupted);

        return log.getBuffer();
    }

    private void deleteLocalOnlyRoles(String conf, Set<String> localOnlyRoles, ScriptLogger log, boolean[] interrupted) throws RepositoryException {
        if (StringUtils.isBlank(conf) || CollectionUtils.isEmpty(localOnlyRoles)) return;
        final boolean deleteAll = "*".equals(conf.trim());
        final List<String> rolesToDelete = Arrays.asList(StringUtils.split(conf));
        final Set<String> failedToDelete = deleteAll ? new HashSet<>(localOnlyRoles) : new HashSet<>(rolesToDelete);

        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, new JCRCallback<Void>() {
            @Override
            public Void doInJCR(JCRSessionWrapper session) throws RepositoryException {
                final QueryWrapper query = session.getWorkspace().getQueryManager().createQuery(STMT, Query.JCR_SQL2);
                final JCRNodeIteratorWrapper nodes = query.execute().getNodes();
                while (nodes.hasNext()) {
                    if (System.getProperty("interruptScript") != null) {
                        log.info("Script interrupted");
                        System.clearProperty("interruptScript");
                        interrupted[0] = true;
                        return null;
                    }
                    final Node role = nodes.nextNode();
                    final String roleName = role.getName();
                    if (localOnlyRoles.contains(roleName) && (deleteAll || rolesToDelete.contains(roleName))) {
                        role.remove();
                        failedToDelete.remove(roleName);
                        log.info(String.format("   - Deleted the role %s", roleName));
                    }
                }
                session.save();
                return null;
            }
        });

        if (!failedToDelete.isEmpty()) {
            log.error(String.format("   - Failed to delete the role(s): %s", failedToDelete));
        }
    }

    private void createMissingRoles(String conf, Set<String> missingRoles, JSONObject referenceRoles, ScriptLogger log, boolean[] interrupted) throws RepositoryException {
        writeRoles(OPERATION.CREATE, conf, missingRoles, referenceRoles, log, interrupted);
    }

    private void resetRoles(String conf, Set<String> rolesToReset, JSONObject referenceRoles, ScriptLogger log, boolean[] interrupted) throws RepositoryException {
        writeRoles(OPERATION.RESET, conf, rolesToReset, referenceRoles, log, interrupted);
    }

    private void writeRoles(OPERATION operation, String conf, Set<String> candidateRoles, JSONObject referenceRoles, ScriptLogger log, boolean[] interrupted) throws RepositoryException {
        if (StringUtils.isBlank(conf) || CollectionUtils.isEmpty(candidateRoles)) return;
        final boolean writeAll = "*".equals(conf.trim());
        final List<String> rolesToWrite = Arrays.asList(StringUtils.split(conf));
        final Set<String> failedToWrite = writeAll ? new HashSet<>(candidateRoles) : new HashSet<>(rolesToWrite);

        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, new JCRCallback<Void>() {
            @Override
            public Void doInJCR(JCRSessionWrapper session) {
                for (String roleName : candidateRoles) {
                    if (System.getProperty("interruptScript") != null) {
                        log.info("Script interrupted");
                        System.clearProperty("interruptScript");
                        interrupted[0] = true;
                        return null;
                    }
                    if (writeAll || rolesToWrite.contains(roleName)) {
                        try {
                            final JSONObject roleJsonObject = referenceRoles.getJSONObject(roleName);
                            /*
                            If a role and its parent have both to be created, then the creation of the child might fail
                            if the parent is later in the list. This case is not handled, the script would have to be
                            run twice.
                             */
                            final JCRNodeWrapper parent = roleJsonObject.has("parentRole") ?
                                    session.getNode("/roles/" + roleJsonObject.getString("parentRole")) :
                                    session.getNode("/roles");
                            final JCRNodeWrapper role = getOrCreateNode(operation, roleName, parent, "jnt:role");
                            setProperty("j:roleGroup", role, roleJsonObject, operation);
                            setProperty("j:privilegedAccess", role, roleJsonObject, operation);
                            setProperty("j:hidden", role, roleJsonObject, operation);
                            setMultipleProperty("j:nodeTypes", role, roleJsonObject, operation);
                            setMultipleProperty("j:permissionNames", role, roleJsonObject, operation);
                            if (roleJsonObject.has("externalPermissions")) {
                                final JSONObject externalPermissions = roleJsonObject.getJSONObject("externalPermissions");
                                if (operation == OPERATION.RESET) {
                                    final List<JCRNodeWrapper> externalPermissionNodes = JCRContentUtils.getChildrenOfType(role, "jnt:externalPermissions");
                                    if (CollectionUtils.isNotEmpty(externalPermissionNodes)) {
                                        for (JCRNodeWrapper externalPermission : externalPermissionNodes) {
                                            if (!externalPermissions.has(externalPermission.getName()))
                                                externalPermission.remove();
                                        }
                                    }
                                }
                                final Iterator externalPermissionsNames = externalPermissions.keys();
                                while (externalPermissionsNames.hasNext()) {
                                    final String key = (String) externalPermissionsNames.next();
                                    final JSONObject extPerm = externalPermissions.getJSONObject(key);
                                    final JCRNodeWrapper extPermNode = getOrCreateNode(operation, key, role, "jnt:externalPermissions");
                                    setProperty("j:path", extPermNode, extPerm, operation);
                                    setMultipleProperty("j:permissionNames", extPermNode, extPerm, operation);
                                }
                            } else {
                                if (operation == OPERATION.RESET) {
                                    final List<JCRNodeWrapper> externalPermissions = JCRContentUtils.getChildrenOfType(role, "jnt:externalPermissions");
                                    if (CollectionUtils.isNotEmpty(externalPermissions)) {
                                        for (JCRNodeWrapper externalPermission : externalPermissions) {
                                            externalPermission.remove();
                                        }
                                    }
                                }
                            }
                            session.save();
                            failedToWrite.remove(roleName);
                            log.info(String.format("   - %s the role %s", StringUtils.capitalize(operation.actionDone), roleName));
                        } catch (JSONException | RepositoryException e) {
                            log.error(String.format("Impossible to %s the role %s", operation.action, roleName), e);
                            return null;
                        }
                    }
                }
                return null;
            }

            private void setProperty(String pName, Node role, JSONObject json, OPERATION operation) throws JSONException, RepositoryException {
                if (json.has(pName))
                    role.setProperty(pName, json.getString(pName));
                else if (operation == OPERATION.RESET && role.hasProperty(pName))
                    role.getProperty(pName).remove();
            }

            private void setMultipleProperty(String pName, Node role, JSONObject json, OPERATION operation) throws JSONException, RepositoryException {
                if (!json.has(pName)) {
                    if (operation == OPERATION.RESET && role.hasProperty(pName))
                        role.getProperty(pName).remove();
                    return;
                }

                final List<Value> values = new ArrayList<Value>();
                final Object o = json.get(pName);
                if (o instanceof String) {
                    values.add(role.getSession().getValueFactory().createValue((String) o));
                } else if (o instanceof JSONArray) {
                    final JSONArray array = (JSONArray) o;
                    for (int i = 0; i < array.length(); i++) {
                        values.add(role.getSession().getValueFactory().createValue(array.getString(i)));
                    }
                }
                role.setProperty(pName, values.toArray(new Value[values.size()]));
            }

            JCRNodeWrapper getOrCreateNode(OPERATION operation, String nodeName, JCRNodeWrapper parent, String pt) throws RepositoryException {
                switch (operation) {
                    case CREATE:
                        return parent.addNode(nodeName, pt);
                    case RESET:
                        return parent.hasNode(nodeName) ? parent.getNode(nodeName) : parent.addNode(nodeName, pt);
                }
                return null;
            }
        });

        if (!failedToWrite.isEmpty()) {
            log.error(String.format("   - Failed to %s the role(s): %s", operation.action, failedToWrite));
        }
    }

    private enum OPERATION {
        CREATE("create", "created"),
        RESET("reset", "reset");

        private final String action, actionDone;

        OPERATION(String action, String actionDone) {
            this.action = action;
            this.actionDone = actionDone;
        }
    }
}
