package br.edu.utfpr.pb.pw45s.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gera um log de acesso para cada requisição HTTP (método, caminho, status e duração).
 * O nível do log varia conforme o status: INFO (2xx/3xx), WARN (4xx) e ERROR (5xx).
 * Executa logo após o filtro de observação do Spring, assim o log já possui o traceId/spanId da requisição.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            // Exceção não tratada: o status 500 só é definido depois, pelo container, ao encaminhar para /error
            int status = failed ? 500 : response.getStatus();
            Level level = status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;

            log.atLevel(level)
                    .addKeyValue("event", "http.request")
                    .addKeyValue("http.method", request.getMethod())
                    .addKeyValue("http.path", request.getRequestURI())
                    .addKeyValue("http.status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .addKeyValue("client.ip", request.getRemoteAddr())
                    .log("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }

    // Requisições de infraestrutura (health check, console do H2 e Swagger) não geram log de acesso
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/h2-console")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
