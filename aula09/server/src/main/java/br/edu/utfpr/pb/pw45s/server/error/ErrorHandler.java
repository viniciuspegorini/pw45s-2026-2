package br.edu.utfpr.pb.pw45s.server.error;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RestController
@Slf4j
public class ErrorHandler implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public ErrorHandler(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping("error")
    public ApiError handlerError(WebRequest webRequest, HttpServletResponse response) {
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(webRequest,
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE));

        if (attributes.get("status") == null) {
            attributes.put("status", response.getStatus());
        }
        int status = (Integer) attributes.get("status");
        // Erros do cliente (4xx) como WARN e erros do servidor (5xx) como ERROR
        log.atLevel(status >= 500 ? Level.ERROR : Level.WARN)
                .addKeyValue("event", "http.error")
                .addKeyValue("http.status", status)
                .addKeyValue("http.path", attributes.get("path"))
                .log("Error: {}; Path: {}; Status: {}", attributes.get("message"), attributes.get("path"), status);
        return new ApiError( (String) attributes.get("message"),
                (String) attributes.get("path"),
                (Integer) attributes.get("status")
        );
    }
}
