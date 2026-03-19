package com.pgmock.nice.auth;

import com.pgmock.nice.response.NicePaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class NiceAuthValidator {

    public ResponseEntity<?> validate(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    NicePaymentResponse.errorBody("U104", "사용자 인증에 실패하였습니다."));
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)));
            if (!decoded.contains(":") || decoded.split(":").length < 2) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        NicePaymentResponse.errorBody("U104", "clientKey:secretKey 형식이어야 합니다."));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    NicePaymentResponse.errorBody("U104", "Base64 디코딩에 실패했습니다."));
        }
        return null;
    }
}
