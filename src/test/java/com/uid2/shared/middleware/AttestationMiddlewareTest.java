package com.uid2.shared.middleware;

import com.uid2.shared.Const;
import com.uid2.shared.attest.IAttestationTokenService;
import com.uid2.shared.attest.JwtService;
import com.uid2.shared.attest.JwtValidationResponse;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class AttestationMiddlewareTest {
    @Mock
    private IAttestationTokenService attestationTokenService;
    @Mock
    private JwtService jwtService;
    @Mock
    RoutingContext routingContext;
    @Mock
    private HttpServerRequest request;
    @Mock
    private Handler<RoutingContext> nextHandler;
    private OperatorKey operatorKey;

    private final String jwtAudience = "testJwtAudience";
    private final String jwtIssuer = "testJwtIssuer";

    private final HashMap<String, Object> data = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        HashSet<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);

        this.operatorKey = new OperatorKey("key", "name", "contact", "trusted", 1000, false, 999, roles, OperatorType.PUBLIC);

        when(this.request.getHeader(Const.Attestation.AttestationJWTHeader)).thenReturn("dummy jwt");
        when(this.routingContext.request()).thenReturn(this.request);

        this.data.put(AuthMiddleware.API_CLIENT_PROP, this.operatorKey);
        when(this.routingContext.data()).thenReturn(data);
    }

    @Test
    void trustedValidJwtNoRolesReturnsSuccess() throws JwtService.ValidationException {
        var attestationMiddleware = getAttestationMiddleware(true);
        JwtValidationResponse response = new JwtValidationResponse(true);
        when(this.jwtService.validateJwt("dummy jwt", this.jwtAudience, this.jwtIssuer)).thenReturn(response);

        var handler = attestationMiddleware.handle(nextHandler);
        handler.handle(this.routingContext);

        verify(nextHandler).handle(routingContext);
    }

    @Test
    void trustedValidJwtHasRequiredRoleReturnsSuccess() throws JwtService.ValidationException {
        var attestationMiddleware = getAttestationMiddleware(true);
        JwtValidationResponse response = new JwtValidationResponse(true)
                .withRoles(Role.OPERATOR, Role.ADMINISTRATOR, Role.OPTOUT);
        when(this.jwtService.validateJwt("dummy jwt", this.jwtAudience, this.jwtIssuer)).thenReturn(response);

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR);
        handler.handle(this.routingContext);

        verify(nextHandler).handle(routingContext);
    }

    @Test
    void trustedValidJwtHasMultipleRolesReturnsSuccess() throws JwtService.ValidationException {
        var attestationMiddleware = getAttestationMiddleware(true);
        JwtValidationResponse response = new JwtValidationResponse(true)
                .withRoles(Role.OPERATOR, Role.ADMINISTRATOR, Role.OPTOUT);
        when(this.jwtService.validateJwt("dummy jwt", this.jwtAudience, this.jwtIssuer)).thenReturn(response);

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR, Role.ADMINISTRATOR);
        handler.handle(this.routingContext);

        verify(nextHandler).handle(routingContext);
    }

    @Test
    void trustedValidJwtMissingRequiredRoleReturns401() throws JwtService.ValidationException {
        var attestationMiddleware = getAttestationMiddleware(true);
        JwtValidationResponse response = new JwtValidationResponse(true)
                .withRoles(Role.OPTOUT);
        when(this.jwtService.validateJwt("dummy jwt", this.jwtAudience, this.jwtIssuer)).thenReturn(response);

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR);
        handler.handle(this.routingContext);

        verifyNoInteractions(nextHandler);
        verify(routingContext).fail(401);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void trustedNoJwtAndJwtNotEnforcedReturnsSuccess(String jwt) {
        var attestationMiddleware = getAttestationMiddleware(false);
        when(this.request.getHeader(Const.Attestation.AttestationJWTHeader)).thenReturn(jwt);

        var handler = attestationMiddleware.handle(nextHandler);
        handler.handle(this.routingContext);

        verify(nextHandler).handle(routingContext);
        verifyNoInteractions(this.jwtService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void trustedNoJwtAndJwtEnforcedReturnsSuccess(String jwt) {
        var attestationMiddleware = getAttestationMiddleware(true);
        when(this.request.getHeader(Const.Attestation.AttestationJWTHeader)).thenReturn(jwt);

        var handler = attestationMiddleware.handle(nextHandler);
        handler.handle(this.routingContext);

        verifyNoInteractions(nextHandler);
        verify(routingContext).fail(401);
    }

    @Test
    void trustedInvalidJwtReturns401() throws JwtService.ValidationException {
        var attestationMiddleware = getAttestationMiddleware(true);
        JwtValidationResponse response = new JwtValidationResponse(false);
        when(this.jwtService.validateJwt("dummy jwt", this.jwtAudience, this.jwtIssuer)).thenReturn(response);

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR);
        handler.handle(this.routingContext);

        verifyNoInteractions(nextHandler);
        verify(routingContext).fail(401);
    }

    @Test
    void trustedJwtValidationThrowsErrorReturns401() throws JwtService.ValidationException {
        var attestationMiddleware = getAttestationMiddleware(true);
        when(this.jwtService.validateJwt("dummy jwt", this.jwtAudience, this.jwtIssuer)).thenThrow(this.jwtService.new ValidationException(Optional.of("test error")));

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR);
        handler.handle(this.routingContext);

        verifyNoInteractions(nextHandler);
        verify(routingContext).fail(401);
    }

    @Test
    void notTrustedNoAttestationTokenReturns401() throws JwtService.ValidationException {
        this.operatorKey = new OperatorKey("key", "name", "contact", "not-trusted", 1000, false, 999, null, OperatorType.PUBLIC);
        this.data.put(AuthMiddleware.API_CLIENT_PROP, this.operatorKey);

        var attestationMiddleware = getAttestationMiddleware(true);

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR);
        handler.handle(this.routingContext);

        verifyNoInteractions(nextHandler);
        verify(routingContext).fail(401);
    }

    @Test
    void notTrustedWithAttestationTokenReturns401() throws JwtService.ValidationException {
        this.operatorKey = new OperatorKey("key", "name", "contact", "not-trusted", 1000, false, 999, null, OperatorType.PUBLIC);
        this.data.put(AuthMiddleware.API_CLIENT_PROP, this.operatorKey);
        when(this.request.getHeader(Const.Attestation.AttestationTokenHeader)).thenReturn("dummy attestation token");
        when(this.request.getHeader("Authorization")).thenReturn("BEARER dummy");
        when(this.attestationTokenService.validateToken("dummy", "dummy attestation token")).thenReturn(false);

        var attestationMiddleware = getAttestationMiddleware(true);

        var handler = attestationMiddleware.handle(nextHandler, Role.OPERATOR);
        handler.handle(this.routingContext);

        verifyNoInteractions(nextHandler);
        verify(routingContext).fail(401);
    }

    private AttestationMiddleware getAttestationMiddleware(boolean enforceJwt) {
        return new AttestationMiddleware(this.attestationTokenService, this.jwtService, this.jwtAudience, this.jwtIssuer, enforceJwt);
    }
}
