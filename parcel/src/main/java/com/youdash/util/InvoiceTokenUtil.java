package com.youdash.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class InvoiceTokenUtil {

    @Value("${invoice.hmac.secret:Youd@sh#Inv0ice$2024!XkQ9}")
    private String secret;

    public String generate(String lrNumber) {
        if (lrNumber == null || lrNumber.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(lrNumber.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Invoice token generation failed", e);
        }
    }

    public boolean verify(String lrNumber, String token) {
        if (token == null || token.isBlank()) return false;
        String expected = generate(lrNumber);
        return expected != null && expected.equalsIgnoreCase(token.trim());
    }
}
