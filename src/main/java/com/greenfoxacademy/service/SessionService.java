package com.greenfoxacademy.service;

import com.greenfoxacademy.constants.AuthCodes;
import com.greenfoxacademy.constants.UserRoles;
import com.greenfoxacademy.constants.Valid;
import com.greenfoxacademy.domain.GenericToken;
import com.greenfoxacademy.domain.Session;
import com.greenfoxacademy.domain.User;
import com.greenfoxacademy.repository.SessionRepository;
import com.greenfoxacademy.requests.AuthRequest;
import com.greenfoxacademy.responses.*;
import com.greenfoxacademy.responses.Error;
import com.sendgrid.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Function;

/**
 * Created by Lenovo on 1/31/2017.
 */
@Service
public class SessionService extends BaseService {
    private final SessionRepository sessionRepository;
    private SecureRandom random = new SecureRandom();
    private UserService userService;

    @Autowired
    public SessionService(SessionRepository sessionRepository,
                          UserService userService) {
        this.sessionRepository = sessionRepository;
        this.userService = userService;
    }

    public Session createSession(User currentUser) {
        Session currentSession = new Session(generateToken(), currentUser);
        saveSession(currentSession);
        return currentSession;
    }

    public void deleteSession(HttpHeaders headers) {
        Session currentSession = extractSessionTokenFromHeader(headers);
        sessionRepository.delete(currentSession);
    }

    public String generateToken() {
        return new BigInteger(130, random).toString(32);
    }

    public void saveSession(Session currentSession) {
        sessionRepository.save(currentSession);
    }

    public boolean tokenExists(String token, Function<String, GenericToken> findFunction) {
        return findFunction.apply(token) != null;
    }

    public Long obtainUserIdFromHeaderToken(HttpHeaders headers) {
        return sessionRepository.findOne(headers
                .getFirst("session_token"))
                .getUser()
                .getId();
    }

    public Session extractSessionTokenFromHeader(HttpHeaders headers) {
        return sessionRepository.findOne(headers
                .getFirst("session_token"));
    }

    public String obtainUserRoleFromToken(String token) {
        return sessionRepository.findOne(token).getUser().getUserRole();
    }

    public HttpHeaders generateHeaders() {
        HttpHeaders responseHeaders = new HttpHeaders();
        URI location = URI.create("https://raptor-konnekt.herokuapp.com");
        responseHeaders.setLocation(location);
        return responseHeaders;
    }

    public HttpHeaders generateHeadersWithToken(String token) {
        HttpHeaders responseHeadersWithToken = generateHeaders();
        responseHeadersWithToken.set("session_token", token);
        return responseHeadersWithToken;
    }

    public int sessionIsValid(HttpHeaders headers, boolean requireAdmin) {
        String token = headers.getFirst("session_token");
        return sessionTokenIsValid(token, sessionRepository::findOne, requireAdmin);
    }

    public int sessionTokenIsValid(String token, Function<String, GenericToken> findFunction, boolean requireAdmin) {
        if (token == null) {
            return AuthCodes.SESSION_TOKEN_NOT_PRESENT;
        } else if (!tokenExists(token, findFunction)) {
            return AuthCodes.SESSION_TOKEN_NOT_REGISTERED;
        } else if (tokenIsExpired(token, findFunction)) {
            return AuthCodes.SESSION_TOKEN_EXPIRED;
        }
        if (requireAdmin) {
            return (obtainUserRoleFromToken(token).equals(UserRoles.ADMIN)) ?
                    AuthCodes.OK :
                    AuthCodes.INSUFFICIENT_PRIVILEGES;
        }
        return AuthCodes.OK;
    }

    private boolean tokenIsExpired(String token, Function<String, GenericToken> findFunction) {
        Date currentTime = new Date();
        return (currentTime.after(findFunction.apply(token).getValid()));
    }

    Response generateEmptyResponse() {
        return new Response(400,
                "",
                new HashMap<String, String>() {{
                    put("", "");
                }});
    }

    public ResponseEntity generateSuccessfulLogin(AuthRequest request) {
        return showSuccessfulAuthResults(userService.findUserByEmail(request.getEmail()));
    }

    private ResponseEntity showSuccessfulAuthResults(User user) {
        Session currentSession = createSession(user);
        return new ResponseEntity<>(new UserResponse(user.getId()),
                generateHeadersWithToken(currentSession.getToken()),
                HttpStatus.CREATED);
    }

    public ResponseEntity generateSuccessfulRegistration(AuthRequest request) {
        return showSuccessfulAuthResults(userService.createUser(request));
    }

    public ResponseEntity generateRegistrationError(ArrayList<Valid.issues>[] issues) {
        return showCustomResults(createErrorResponse(issues), HttpStatus.FORBIDDEN);
    }

    private ErrorResponse createErrorResponse(ArrayList<Valid.issues>[] issues) {
        ErrorResponse errorResponse = new ErrorResponse();
        return errorResponse.addErrorMessages(issues, ErrorResponse.AuthType.REGISTRATION);
    }

    public ResponseEntity respondWithNotAuthenticated(int authResult) {
        NotAuthenticatedErrorResponse notAuthResponse =
                new NotAuthenticatedErrorResponse(
                        new Error("Authentication error", "Not authenticated"));
        notAuthResponse.addErrorMessages(authResult);
        return new ResponseEntity<>(notAuthResponse,
                generateHeaders(),
                HttpStatus.UNAUTHORIZED);
    }
}