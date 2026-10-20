package cn.sparkden.weread;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegistrationTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final Accounts accounts = new Accounts(db, encoder, "", "");

    @Test void rejectsInvalidNamesAndPasswordsBeforeHashing() {
        for (String name : new String[]{null, "", "ab", "空格", "abc def", "a".repeat(65)})
            assertEquals(400, assertThrows(ApiException.class, () -> accounts.register(name, "secure-password-123")).status());
        for (String password : new String[]{null, "", " ".repeat(12), "a".repeat(73), "密".repeat(25)})
            assertEquals(400, assertThrows(ApiException.class, () -> accounts.register("reader", password)).status());
        verifyNoInteractions(db, encoder);
    }
    @Test void acceptsShortNonBlankPassword() {
        when(encoder.encode("a")).thenReturn("encoded-short");
        accounts.register("reader_short", "a");
        verify(db).update(eq("INSERT INTO app_user(id,username,password_hash) VALUES (?,?,?)"), any(java.util.UUID.class), eq("reader_short"), eq("encoded-short"));
    }
    @Test void storesOnlyHash() {
        when(encoder.encode("secure-password-123")).thenReturn("encoded-hash");
        accounts.register("reader_1", "secure-password-123");
        verify(db).update(eq("INSERT INTO app_user(id,username,password_hash) VALUES (?,?,?)"), any(java.util.UUID.class), eq("reader_1"), eq("encoded-hash"));
    }
    @Test void duplicateNameIsConflictNotServerError() {
        when(encoder.encode(anyString())).thenReturn("hash");
        when(db.update(anyString(), any(java.util.UUID.class), eq("reader"), eq("hash"))).thenThrow(new DuplicateKeyException("internal detail"));
        var error = assertThrows(ApiException.class, () -> accounts.register("reader", "secure-password-123"));
        assertEquals(409, error.status());
        assertFalse(error.getMessage().contains("internal"));
    }
    @Test void registrationBodyDoesNotLogPassword() {
        assertFalse(new ApiController.Registration("reader", "secret").toString().contains("secret"));
    }
}
