package org.keycloak.adapters.undertow;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletSessionConfig;
import java.io.ByteArrayInputStream;
import org.jboss.logging.Logger;
import org.keycloak.adapters.AdapterConstants;
import org.keycloak.adapters.config.RealmConfiguration;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.adapters.config.RealmConfigurationLoader;

import javax.servlet.ServletContext;
import java.io.InputStream;
import java.util.Map;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class KeycloakServletExtension implements ServletExtension {

    protected static Logger log = Logger.getLogger(KeycloakServletExtension.class);

    // todo when this DeploymentInfo method of the same name is fixed.
    public boolean isAuthenticationMechanismPresent(DeploymentInfo deploymentInfo, final String mechanismName) {
        LoginConfig loginConfig = deploymentInfo.getLoginConfig();
        if(loginConfig != null) {
            for(AuthMethodConfig method : loginConfig.getAuthMethods()) {
                if(method.getName().equalsIgnoreCase(mechanismName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private InputStream getJSONFromServletContext(ServletContext servletContext) {
        String json = servletContext.getInitParameter(AdapterConstants.AUTH_DATA_PARAM_NAME);
        if (json == null) {
            return null;
        }
        return new ByteArrayInputStream(json.getBytes());
    }

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
        if (!isAuthenticationMechanismPresent(deploymentInfo, "KEYCLOAK")) {
            log.info("auth-method is not keycloak!");
            return;
        }
        log.info("KeycloakServletException initialization");
        InputStream is = getJSONFromServletContext(servletContext);
        if (is == null) {
            is = servletContext.getResourceAsStream("/WEB-INF/keycloak.json");
        }
        if (is == null) throw new RuntimeException("Unable to find realm config in /WEB-INF/keycloak.json or in keycloak subsystem.");
        RealmConfigurationLoader loader = new RealmConfigurationLoader(is);
        loader.init(true);
        AdapterConfig keycloakConfig = loader.getAdapterConfig();
        RealmConfiguration realmConfiguration = loader.getRealmConfiguration();
        PreflightCorsHandler.Wrapper preflight = new PreflightCorsHandler.Wrapper(keycloakConfig);
        UserSessionManagement userSessionManagement = new UserSessionManagement(realmConfiguration);
        ServletKeycloakAuthenticationMechanism auth = null;
        auth = new ServletKeycloakAuthenticationMechanism(
                userSessionManagement,
                keycloakConfig,
                realmConfiguration,
                deploymentInfo.getConfidentialPortManager());
        AuthenticatedActionsHandler.Wrapper actions = new AuthenticatedActionsHandler.Wrapper(keycloakConfig);

        // setup handlers

        deploymentInfo.addInitialHandlerChainWrapper(preflight); // cors preflight
        deploymentInfo.addOuterHandlerChainWrapper(new ServletAdminActionsHandler.Wrapper(realmConfiguration, loader.getResourceMetadata(), userSessionManagement));
        final ServletKeycloakAuthenticationMechanism theAuth = auth;
        deploymentInfo.addAuthenticationMechanism("KEYCLOAK", new AuthenticationMechanismFactory() {
            @Override
            public AuthenticationMechanism create(String s, FormParserFactory formParserFactory, Map<String, String> stringStringMap) {
                return theAuth;
            }
        }); // authentication
        deploymentInfo.addInnerHandlerChainWrapper(ServletPropagateSessionHandler.WRAPPER); // propagates SkeletonKeySession
        deploymentInfo.addInnerHandlerChainWrapper(actions); // handles authenticated actions and cors.

        deploymentInfo.setIdentityManager(new KeycloakIdentityManager(keycloakConfig, realmConfiguration));

        log.info("Setting jsession cookie path to: " + deploymentInfo.getContextPath());
        ServletSessionConfig cookieConfig = new ServletSessionConfig();
        cookieConfig.setPath(deploymentInfo.getContextPath());
        deploymentInfo.setServletSessionConfig(cookieConfig);
    }
}
