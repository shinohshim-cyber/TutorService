package com.tutorservice.identity;
import com.tutorservice.identity.service.PasswordPolicy;
import com.tutorservice.common.web.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordPolicyTest {
    @Test void utf8BoundaryAndMinimum() {
        assertDoesNotThrow(()->PasswordPolicy.check("가".repeat(24),true));
        assertThrows(ApiException.class,()->PasswordPolicy.check("가".repeat(25),true));
        assertDoesNotThrow(()->PasswordPolicy.check("a".repeat(72),true));
        assertThrows(ApiException.class,()->PasswordPolicy.check("a".repeat(73),true));
        assertThrows(ApiException.class,()->PasswordPolicy.check("short",true));
        assertThrows(ApiException.class,()->PasswordPolicy.check(null,false));
    }
}
