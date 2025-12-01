package com.example.auto.util;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 네이버 커머스 API 전자서명 유틸
 *
 * 규칙:
 *   password = clientId + "_" + timestamp
 *   hashed   = bcrypt.hashpw(password, clientSecret)
 *   sign     = Base64.encode(hashed)
 */
public class SignatureUtil {

    /**
     * 네이버 커머스 API용 client_secret_sign 생성
     *
     * @param clientId     네이버 커머스 client_id
     * @param timestamp    밀리초 timestamp
     * @param clientSecret 네이버 커머스 client_secret (bcrypt salt 문자열)
     * @return client_secret_sign (Base64 인코딩된 bcrypt 결과)
     */
    public static String generateSignature(String clientId,
                                           long timestamp,
                                           String clientSecret) {

        // 1. password = clientId + "_" + timestamp
        String password = clientId + "_" + timestamp;

        // 2. bcrypt(password, clientSecret)
        String bcryptHash = BCrypt.hashpw(password, clientSecret);

        // 3. Base64 인코딩
        return Base64.getEncoder()
                .encodeToString(bcryptHash.getBytes(StandardCharsets.UTF_8));
    }

    private SignatureUtil() {
        // 유틸 클래스이므로 인스턴스 생성 방지
    }
}
