package br.edu.utfpr.pb.pw45s.server.security;

import br.edu.utfpr.pb.pw45s.server.model.User;
import br.edu.utfpr.pb.pw45s.server.service.AuthService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class JWTAuthorizationFilter extends BasicAuthenticationFilter {

    private final AuthService authService;

    public JWTAuthorizationFilter(AuthenticationManager authenticationManager,
                                  AuthService authService) {
        super(authenticationManager);
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String header = request.getHeader(SecurityConstants.HEADER_STRING);
        if (header == null || !header.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                getAuthentication(request);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken
                getAuthentication(HttpServletRequest request) {
        String token = request.getHeader(SecurityConstants.HEADER_STRING);
        if (token != null) {
            String username;
            try {
                username = JWT.require(Algorithm.HMAC512(SecurityConstants.SECRET))
                        .build()
                        .verify(token.replace(SecurityConstants.TOKEN_PREFIX, ""))
                        .getSubject();
            } catch (JWTVerificationException e) {
                log.atWarn()
                        .addKeyValue("event", "auth.token.invalid")
                        .addKeyValue("auth.reason", e.getClass().getSimpleName())
                        .addKeyValue("http.path", request.getRequestURI())
                        .log("Invalid JWT token on {}: {}", request.getRequestURI(), e.getMessage());
                throw e;
            }
            if (username != null) {
                User user = (User) authService.loadUserByUsername(username);
                return new UsernamePasswordAuthenticationToken(username, null,
                                    user.getAuthorities());
            }
        }
        return null;
    }
}
