package br.edu.utfpr.pb.pw45s.server.security.social;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;

@Component
@Slf4j
public class GoogleTokenVerifier {

    private static final HttpTransport transport = new NetHttpTransport();
    private static final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    private static final String CLIENT_ID = "310109923674-la5thl4s4t0b2ajp6acdhq7tra74dn31.apps.googleusercontent.com";

    public Payload verify(String idTokenString)
            throws GeneralSecurityException, IOException {
        return GoogleTokenVerifier.verifyToken(idTokenString);
    }

    private static Payload verifyToken(String idTokenString)
            throws GeneralSecurityException, IOException {
        final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier
                                    .Builder(transport, jsonFactory)
                                        .setIssuers(Arrays.asList("https://accounts.google.com",
                                                "accounts.google.com"))
                                        .setAudience(Collections.singletonList(CLIENT_ID))
                                        .build();
        // O token não é registrado no log, pois é uma credencial
        log.atInfo()
                .addKeyValue("event", "auth.google.verify")
                .log("Validating Google idToken");
        GoogleIdToken idToken = null;
        try {
          idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("event", "auth.google.verify_failed")
                    .setCause(e)
                    .log("Error validating Google idToken");
        }
        if (idToken == null) {
            log.atWarn()
                    .addKeyValue("event", "auth.google.invalid_token")
                    .log("Google idToken is invalid");
            throw new RuntimeException("Google idToken is invalid.");
        }
        return idToken.getPayload();
    }
}