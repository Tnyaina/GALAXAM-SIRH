package mg.galaxam.sirh.paieconfig.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import mg.galaxam.sirh.organisation.api.OrganisationController;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ParametreGlobalAuthorizationTest {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultMethodSecurityExpressionHandler HANDLER = new DefaultMethodSecurityExpressionHandler();
    private static final List<String> ENDPOINTS = List.of(
            "getAllParametresGlobaux", "getParametreGlobalById", "toggleGroupeActif",
            "createParametreGlobal", "updateParametreGlobal", "toggleActif", "annulerProgramme");

    @Test
    void everyParameterEndpointIsRestrictedToAdmin() throws Exception {
        for (String endpointName : ENDPOINTS) {
            Method endpoint = endpoint(endpointName);
            PreAuthorize policy = endpoint.getAnnotation(PreAuthorize.class);
            assertNotNull(policy, endpointName + " must declare an authorization policy");
            assertTrue(isAllowed(endpoint, "ROLE_ADMIN"), endpointName + " should allow admin");
            assertFalse(isAllowed(endpoint, "ROLE_AGENT_POINTAGE"), endpointName + " must deny the pointage agent");
            assertFalse(isAllowed(endpoint, "PAIE_READ"), endpointName + " must deny a payroll reader");
            assertFalse(isAllowed(endpoint, "PAIE_WRITE"), endpointName + " must deny a payroll writer");
            assertFalse(isAllowed(endpoint, "ORGANISATION_WRITE"), endpointName + " must deny organization writers");
            assertFalse(isAllowed(endpoint, "POINTAGE_WRITE"), endpointName + " must deny pointage writers");
        }
    }

    @Test
    void pointageAgentCanReadSitesButCannotManageOrganization() throws Exception {
        Method readSites = OrganisationController.class.getMethod("getAllSites", Integer.class, Boolean.class, String.class);
        Method createSite = OrganisationController.class.getMethod("createSite",
                mg.galaxam.sirh.organisation.api.dto.SiteRequest.class);
        assertTrue(isAllowed(readSites, "ROLE_AGENT_POINTAGE"));
        assertFalse(isAllowed(createSite, "ROLE_AGENT_POINTAGE"));
    }

    private static boolean isAllowed(Method endpoint, String authority) {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(endpoint);
        when(invocation.getThis()).thenReturn(new ParametreGlobalController());
        when(invocation.getArguments()).thenReturn(new Object[0]);
        var authentication = new UsernamePasswordAuthenticationToken(
                "test-user", "n/a", List.of(new SimpleGrantedAuthority(authority)));
        var context = HANDLER.createEvaluationContext(() -> authentication, invocation);
        return Boolean.TRUE.equals(PARSER.parseExpression(endpoint.getAnnotation(PreAuthorize.class).value())
                .getValue(context, Boolean.class));
    }

    private static Method endpoint(String name) throws NoSuchMethodException {
        return java.util.Arrays.stream(ParametreGlobalController.class.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(name));
    }
}
