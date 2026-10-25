package mg.galaxam.sirh.pointage.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;

class PointageDerogationAuthorizationTest {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Method ENDPOINT = endpointMethod();
    private static final MethodInvocation INVOCATION = invocation();
    private static final DefaultMethodSecurityExpressionHandler HANDLER = new DefaultMethodSecurityExpressionHandler();

    @Test
    void agentPointageCannotReadEmployeeAllowanceDerogations() {
        assertFalse(isAllowed("ROLE_AGENT_POINTAGE"));
    }

    @Test
    void payrollReaderCanReadEmployeeAllowanceDerogations() {
        assertTrue(isAllowed("PAIE_READ"));
    }

    @Test
    void adminCanReadEmployeeAllowanceDerogations() {
        assertTrue(isAllowed("ROLE_ADMIN"));
    }

    private static boolean isAllowed(String authority) {
        PreAuthorize policy = ENDPOINT.getAnnotation(PreAuthorize.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "test-user", "n/a", List.of(new SimpleGrantedAuthority(authority)));
        var context = HANDLER.createEvaluationContext(() -> authentication, INVOCATION);
        return Boolean.TRUE.equals(PARSER.parseExpression(policy.value()).getValue(context, Boolean.class));
    }

    private static Method endpointMethod() {
        try {
            return PointageController.class.getMethod("getDerogations");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodInvocation invocation() {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(ENDPOINT);
        when(invocation.getThis()).thenReturn(new PointageController());
        when(invocation.getArguments()).thenReturn(new Object[0]);
        return invocation;
    }
}
