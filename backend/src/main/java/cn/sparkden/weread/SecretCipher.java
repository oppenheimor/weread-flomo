package cn.sparkden.weread;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public SecretCipher(@Value("${app.credential-key}") String encoded) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("CREDENTIAL_ENCRYPTION_KEY 必须是 32 字节随机密钥的 Base64 编码"); }
        if (bytes.length != 32) throw new IllegalStateException("请先配置 CREDENTIAL_ENCRYPTION_KEY；不要使用 API Key 作为加密主密钥");
        key = new SecretKeySpec(bytes, "AES"); Arrays.fill(bytes, (byte) 0);
    }
    private byte[] aad(UUID user, String provider) { return ("weread-flomo:v1:" + user + ":" + provider).getBytes(StandardCharsets.UTF_8); }
    public String encrypt(UUID user, String provider, String plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv)); cipher.updateAAD(aad(user, provider));
            return "v1." + Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new ApiException(500, "凭证加密失败，未保存"); }
    }
    public String decrypt(UUID user, String provider, String encoded) {
        try {
            var parts = encoded.split("\\.");
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            if (iv.length != 12) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv)); cipher.updateAAD(aad(user, provider));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new ApiException(503, "无法解密账号凭证，请检查服务端加密主密钥或重新设置凭证"); }
    }
}
