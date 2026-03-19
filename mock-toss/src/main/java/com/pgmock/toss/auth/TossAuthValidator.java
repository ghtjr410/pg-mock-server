package com.pgmock.toss.auth;

import com.pgmock.toss.response.TossPaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class TossAuthValidator {

    public ResponseEntity<?> validate(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    TossPaymentResponse.errorBody("UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다."));
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)));
            if (!decoded.endsWith(":")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        TossPaymentResponse.errorBody("UNAUTHORIZED_KEY", "시크릿 키 형식이 올바르지 않습니다. {secretKey}: 형식이어야 합니다."));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    TossPaymentResponse.errorBody("UNAUTHORIZED_KEY", "Base64 디코딩에 실패했습니다."));
        }
        return null;
    }
}
