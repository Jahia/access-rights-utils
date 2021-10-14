import org.apache.commons.lang.StringUtils
import org.jahia.services.SpringContextSingleton

def rolesUtils = SpringContextSingleton.getBean("accessrightsutils.roleutils")

if (StringUtils.isNotBlank(dumpedRolesPath)) {
    for (String msg : rolesUtils.runRolesComparison(dumpedRolesPath, deleteLocalOnlyRoles, createMissingRoles, resetDifferentRoles)) {
        log.info(msg)
    }
} else {
    log.warn("No JSON file specified")
}