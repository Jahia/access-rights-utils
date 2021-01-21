import org.apache.commons.lang.StringUtils
import org.jahia.services.SpringContextSingleton

def rolesUtils = SpringContextSingleton.getBean("accessrightsutils.roleutils")
if (dumpRoles) {
    rolesUtils.dumpRolesToFile().each() {
        msg -> log.info(msg)
    }
}

if (StringUtils.isNotBlank(dumpedRolesPath)) {
    for (String msg : rolesUtils.runRolesComparison(dumpedRolesPath, deleteLocalOnlyRoles, createMissingRoles, resetDifferentRoles)) {
        log.info(msg)
    }
}

if (StringUtils.isBlank(dumpRoles) && StringUtils.isBlank(dumpedRolesPath)) {
    log.info("No action executed")
}