package cn.sparkden.weread;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {
    private final SecretCipher cipher=new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    @Test void roundTripUsesRandomIvAndNeverStoresPlaintext() {
        UUID user=UUID.randomUUID(); String secret="wrk-test-secret";
        String a=cipher.encrypt(user,"weread",secret), b=cipher.encrypt(user,"weread",secret);
        assertNotEquals(a,b); assertFalse(a.contains(secret)); assertEquals(secret,cipher.decrypt(user,"weread",a));
    }
    @Test void userAndProviderAreAuthenticated() {
        UUID user=UUID.randomUUID(); String value=cipher.encrypt(user,"weread","wrk-secret");
        assertThrows(ApiException.class,() -> cipher.decrypt(UUID.randomUUID(),"weread",value));
        assertThrows(ApiException.class,() -> cipher.decrypt(user,"flomo",value));
    }
    @Test void wrongMasterKeyAndTamperingFailClosed() {
        UUID user=UUID.randomUUID(); String value=cipher.encrypt(user,"weread","secret");
        byte[] other=new byte[32]; Arrays.fill(other,(byte)1);
        var wrong=new SecretCipher(Base64.getEncoder().encodeToString(other));
        assertThrows(ApiException.class,() -> wrong.decrypt(user,"weread",value));
        assertThrows(ApiException.class,() -> cipher.decrypt(user,"weread",value.substring(0,value.length()-8)+"AAAAAAAA"));
        assertThrows(IllegalStateException.class,() -> new SecretCipher(""));
    }
    @Test void changeObjectsDoNotLeakSecretsWhenLogged() {
        assertFalse(new Credentials.Change("wrk-secret","https://flomoapp.com/iwh/1/secret/").toString().contains("secret"));
    }
}
