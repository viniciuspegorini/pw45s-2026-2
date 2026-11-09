package br.edu.utfpr.pb.pw45s.server.security.social;

import br.edu.utfpr.pb.pw45s.server.model.AuthProvider;
import br.edu.utfpr.pb.pw45s.server.model.User;
import br.edu.utfpr.pb.pw45s.server.repository.AuthorityRepository;
import br.edu.utfpr.pb.pw45s.server.repository.UserRepository;
import br.edu.utfpr.pb.pw45s.server.security.SecurityConstants;
import br.edu.utfpr.pb.pw45s.server.security.dto.AuthenticationResponse;
import br.edu.utfpr.pb.pw45s.server.security.dto.UserResponseDTO;
import br.edu.utfpr.pb.pw45s.server.service.UserService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashSet;

@RestController
@RequestMapping("auth-social")
@Slf4j
public class AuthController {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;

    public AuthController(GoogleTokenVerifier googleTokenVerifier,
                          UserService userService,
                          UserRepository userRepository,
                          AuthorityRepository authorityRepository) {
        this.googleTokenVerifier = googleTokenVerifier;
        this.userService = userService;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
    }

    @PostMapping
    public ResponseEntity<AuthenticationResponse> auth(HttpServletRequest request, HttpServletResponse response) {
        String idToken = request.getHeader("Auth-Id-Token");
        if (idToken != null) {
            final Payload payload;
            try {
                payload = googleTokenVerifier.verify(
                        idToken.replace(SecurityConstants.TOKEN_PREFIX, "") );
                if (payload != null) {
                    String username = payload.getEmail();

                    User user = userRepository.findUserByUsername(username);
                    if (user == null) {
                        user = new User();
                        user.setUsername(username);
                        user.setDisplayName( (String) payload.get("name"));
                        user.setPassword("P4ssword");
                        user.setProvider(AuthProvider.google);
                        user.setUserAuthorities( new HashSet<>() );
                        user.getUserAuthorities().add(
                                authorityRepository.findByAuthority("ROLE_USER")
                        );
                        userService.save(user);
                        log.atInfo()
                                .addKeyValue("event", "user.created")
                                .addKeyValue("auth.provider", "google")
                                .addKeyValue("user.name", username)
                                .log("New user {} created from Google login", username);
                    }
                    String token = JWT.create()
                            .withSubject( username  )
                            .withExpiresAt(
                                    new Date(System.currentTimeMillis() +
                                    SecurityConstants.EXPIRATION_TIME
                                    ))
                            .sign(Algorithm.HMAC512(SecurityConstants.SECRET));

                    log.atInfo()
                            .addKeyValue("event", "auth.login.success")
                            .addKeyValue("auth.provider", "google")
                            .addKeyValue("user.name", username)
                            .log("User {} authenticated with Google", username);
                    return ResponseEntity.ok(
                            new AuthenticationResponse(token, new UserResponseDTO(user)));

                }
            } catch (Exception e) {
                log.atError()
                        .addKeyValue("event", "auth.login.error")
                        .addKeyValue("auth.provider", "google")
                        .setCause(e)
                        .log("Error during Google authentication: {}", e.getMessage());
            }
        }
        log.atWarn()
                .addKeyValue("event", "auth.login.failure")
                .addKeyValue("auth.provider", "google")
                .addKeyValue("auth.reason", idToken == null ? "MissingIdToken" : "InvalidIdToken")
                .log("Google authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
    }
}
