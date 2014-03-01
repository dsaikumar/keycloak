package org.keycloak.services.resources.admin;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.logging.Logger;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ApplicationModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.Auth;
import org.keycloak.services.managers.ModelToRepresentation;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.managers.ResourceAdminManager;
import org.keycloak.services.managers.TokenManager;

import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class RealmAdminResource {
    protected static final Logger logger = Logger.getLogger(RealmAdminResource.class);
    protected RealmAuth auth;
    protected RealmModel realm;
    private TokenManager tokenManager;

    @Context
    protected ResourceContext resourceContext;

    @Context
    protected KeycloakSession session;

    public RealmAdminResource(RealmAuth auth, RealmModel realm, TokenManager tokenManager) {
        this.auth = auth;
        this.realm = realm;
        this.tokenManager = tokenManager;

        auth.init(RealmAuth.Resource.REALM);
    }

    @Path("applications")
    public ApplicationsResource getApplications() {
        ApplicationsResource applicationsResource = new ApplicationsResource(realm, auth);
        resourceContext.initResource(applicationsResource);
        return applicationsResource;
    }

    @Path("oauth-clients")
    public OAuthClientsResource getOAuthClients() {
        OAuthClientsResource oauth = new OAuthClientsResource(realm, auth, session);
        resourceContext.initResource(oauth);
        return oauth;
    }

    @Path("roles")
    public RoleContainerResource getRoleContainerResource() {
        return new RoleContainerResource(realm, auth, realm);
    }

    @GET
    @NoCache
    @Produces("application/json")
    public RealmRepresentation getRealm() {
        if (auth.hasView()) {
            return ModelToRepresentation.toRepresentation(realm);
        } else {
            auth.requireAny();

            RealmRepresentation rep = new RealmRepresentation();
            rep.setRealm(realm.getName());

            return rep;
        }
    }

    @PUT
    @Consumes("application/json")
    public void updateRealm(final RealmRepresentation rep) {
        auth.requireManage();

        logger.debug("updating realm: " + realm.getName());
        new RealmManager(session).updateRealm(rep, realm);
    }

    @DELETE
    public void deleteRealm() {
        auth.requireManage();

        if (!new RealmManager(session).removeRealm(realm)) {
            throw new NotFoundException();
        }
    }

    @Path("users")
    public UsersResource users() {
        UsersResource users = new UsersResource(realm, auth, tokenManager);
        resourceContext.initResource(users);
        return users;
    }

    @Path("roles-by-id")
    public RoleByIdResource rolesById() {
        RoleByIdResource resource = new RoleByIdResource(realm, auth);
        resourceContext.initResource(resource);
        return resource;
    }

    @Path("push-revocation")
    @POST
    public void pushRevocation() {
        auth.requireManage();
        new ResourceAdminManager().pushRevocationPolicies(realm);
    }

}
