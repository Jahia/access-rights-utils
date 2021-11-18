import org.apache.commons.lang.StringUtils
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRPropertyWrapper
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate
import org.jahia.services.query.QueryWrapper

import javax.jcr.PropertyType
import javax.jcr.RepositoryException
import javax.jcr.Value
import javax.jcr.query.Query

boolean generateCSV = true
boolean doWrite = false
final Map<String, String> rolesMapping = new HashMap<>()
rolesMapping.put("editor", "editor2")

updateAce(Constants.EDIT_WORKSPACE, rolesMapping, doWrite, generateCSV)
if (!generateCSV) {
    updateAce(Constants.LIVE_WORKSPACE, rolesMapping, doWrite, generateCSV)
}

void updateAce(String workspace, Map<String, String> rolesMapping, boolean doWrite, boolean generateCSV) {
    if (!generateCSV) {
        log.info("Scanning the workspace " + workspace)
    }
    JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, new JCRCallback<Object>() {
        @Override
        Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
            final String stmt = "select * from [jnt:ace]"

            QueryWrapper query = session.getWorkspace().getQueryManager().createQuery(
                    stmt,
                    Query.JCR_SQL2
            )
            query.setLimit(-1)

            for (JCRNodeWrapper node : query.execute().getNodes()) {

                if (generateCSV) {
                    if (!node.isNodeType("jnt:externalAce")) {
                        final JCRPropertyWrapper property = node.getProperty("j:roles")
                        for (Value value : property.getValues()) {
                            final String role = value.getString()
                            final String principal = StringUtils.defaultIfBlank(node.getPropertyAsString("j:principal"), "")
                            if (principal.startsWith("u:")){
                                log.info(principal.substring(2).concat(";;").concat(role).concat(";").concat(node.getParent().getParent().getPath()))
                            } else if(principal.startsWith("g:")) {
                                log.info(";".concat(principal.substring(2)).concat(";").concat(role).concat(";").concat(node.getParent().getParent().getPath()))
                            }
                        }
                    }
                } else {
                    handleAce(node)
                }

            }

            return null
        }

        void handleAce(JCRNodeWrapper ace) {
            if (!ace.hasProperty("j:roles"))
                return

            boolean alreadyUpdated = false
            final JCRPropertyWrapper property = ace.getProperty("j:roles")
            for (Value value : property.getValues()) {
                final String role = value.getString()
                if (rolesMapping.containsKey(role)) {
                    String newRole = rolesMapping.get(role)
                    if (!alreadyUpdated) log.info(ace.getPath())
                    log.info(String.format("  %s -> %s", role, newRole))
                    if (!doWrite) continue

                    try {
                        JCRObservationManager.setAllEventListenersDisabled(true)

                        property.removeValue(value)
                        Value newValue = ace.getSession().getValueFactory().createValue(newRole, PropertyType.NAME)
                        property.addValue(newValue)
                        ace.saveSession()
                        if (ace.isNodeType("jnt:externalAce")) {
                            final String nodename = ace.getName()
                            if (!nodename.startsWith("REF"+role+"_")) {
                                log.error("  !! Unexpected node name for an external ACE: " + ace.getPath())
                            } else {
                                final String nodeNewName = "REF"+newRole+nodename.substring(("REF"+role).length())
                                if (ace.getParent().hasNode(nodeNewName)) {
                                    log.error("  !! Impossible to rename the external ACE as it is conflicting with another one")
                                    log.error("      !!! external ACE: " + ace.getPath())
                                    log.error("      !!! conflicting external ACE: " + nodeNewName)
                                } else {
                                    ace.getSession().move(ace.getPath(), ace.getParent().getPath().concat("/").concat(nodeNewName))
                                    ace.saveSession()
                                    log.info("  > renamed the external ACE: " + nodename + " -> " + nodeNewName)
                                }
                            }
                        }
                    } finally {
                        JCRObservationManager.setAllEventListenersDisabled(false)
                    }
                }
            }

        }
    })

    log.info("")
}