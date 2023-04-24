import org.apache.commons.lang.StringUtils
import org.jahia.osgi.BundleUtils;
import org.jahia.services.securityfilter.PermissionService;
import org.jahia.services.securityfilter.ScopeDefinition;

def s = BundleUtils.getOsgiService(PermissionService.class, null)
Collection<ScopeDefinition> scopes = s.getCurrentScopes()
Collection<ScopeDefinition> allScopes = s.getAvailableScopes()

log.info("Active scopes:\n-----")
for (ScopeDefinition scope : scopes) {
    log.info(scope.getScopeName())
}

log.info("\n----------------------------------------------------------------------------------------------------------------------------------------")
for (ScopeDefinition scope : allScopes) {
    log.info("\n" + scope.getScopeName())
    log.info("  description: " + scope.description)
    if (scope.getMetadata() != null && !scope.getMetadata().isEmpty()) {
        log.info("  metadata:")
        scope.getMetadata().forEach((k, v) -> log.info("    " + k + ": " + v))
    }
    if (scope.getApply() != null && !scope.getApply().isEmpty()) {
        log.info("  auto_apply:")
        (scope.getApply() as Collection).forEach(a -> log.info("    - " + printApply(a)))
    }
    if (scope.getConstraints() != null && !scope.getConstraints().isEmpty()) {
        log.info("  constraints:")
        (scope.getConstraints() as Collection).forEach(c -> log.info("    - " + printConstraint(c)))
    }
    if (scope.getGrants() != null && !scope.getGrants().isEmpty()) {
        log.info("  grants:")
        (scope.getGrants() as Collection).forEach(g -> printGrant(g))
    }
}

private String printApply(Object apply) {
    if (apply.class.name == "org.jahia.bundles.securityfilter.core.apply.AlwaysAutoApply") return "always: true"
    if (apply.class.name == "org.jahia.bundles.securityfilter.core.apply.AutoApplyByOrigin") return "origin: " + apply.origin
    return apply.class.name
}

private String printConstraint(Object constraint) {
    if (constraint.class.name == "org.jahia.bundles.securityfilter.core.constraint.PermissionConstraint") return "user_permission: " + constraint.permission + "\n      path: " + constraint.nodePath + (constraint.workspace == null ? "" : "      \nworkspace: " + constraint.workspace)
    if (constraint.class.name == "org.jahia.bundles.securityfilter.core.constraint.PrivilegedConstraint") return "privileged_user: true"
    return constraint.class.name
}

private void printGrant(Object grant) {
    if (grant.class.name == "org.jahia.bundles.securityfilter.core.grant.ApiGrant") printApiGrant(grant)
    else if (grant.class.name == "org.jahia.bundles.securityfilter.core.grant.NodeGrant") printNodeGrant(grant)
    else if (grant.class.name == "org.jahia.bundles.securityfilter.core.AuthorizationConfig\$CompoundGrant") (grant.grants as Collection).forEach(g -> printGrant(g))
    else log.info("    - " + grant.class.name)
}

private void printApiGrant(Object grant) {
    log.info("    - api: " + StringUtils.join(grant.apis as Collection, ", "))
}

private void printNodeGrant(Object grant) {
    if (grant.nodeTypes.isEmpty() &&
            grant.excludesNodeTypes.isEmpty() &&
            grant.pathPatterns.isEmpty() &&
            grant.excludedPathPatterns.size() == 1 && grant.excludedPathPatterns.iterator().next().pattern() == ".*" &&
            grant.workspaces.isEmpty() &&
            grant.withPermission == null)
        log.info("      node: none")
    else {
        log.info("      node:")
        printNodeGrantConstraint("nodeType", grant.nodeTypes)
        printNodeGrantConstraint("excludedNodeType", grant.excludesNodeTypes)
        printNodeGrantConstraint("pathPattern", grant.pathPatterns)
        printNodeGrantConstraint("excludedPathPattern", grant.excludedPathPatterns)
        printNodeGrantConstraint("workspace", grant.workspaces)
        if (grant.withPermission != null) log.info("        withPermission: " + grant.withPermission)
    }
}

private void printNodeGrantConstraint(String key, Collection values) {
    if (values != null && !values.isEmpty()) log.info("        " + key + ": " + StringUtils.join(values, ","))
}
