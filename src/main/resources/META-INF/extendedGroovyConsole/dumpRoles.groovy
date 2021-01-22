import org.jahia.services.SpringContextSingleton

def rolesUtils = SpringContextSingleton.getBean("accessrightsutils.roleutils")
rolesUtils.dumpRolesToFile(dumpLocation).each() {
    msg -> log.info(msg)
}