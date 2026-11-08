package de.consorsbank.consumingwebservice;

import de.consorsbank.consumingwebservice.service.AuthorizationService;
import de.consorsbank.consumingwebservice.wsdl.AuthorizationStatusType;
import de.consorsbank.consumingwebservice.wsdl.TransactionAuthorizationResponseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that proves:
 * 1. The Spring context loads successfully (all beans including JAXB-aware beans).
 * 2. The JAXB-generated classes are on the test compile classpath.
 * 3. The AuthorizationService can produce valid JAXB response objects.
 */
@SpringBootTest
class ApplicationTest {

    @Autowired
    private AuthorizationService authorizationService;

    @Test
    void contextLoads() {
        // If this test passes, the Spring context started — confirming
        // the JAXB classes compiled and are available on the runtime classpath.
        assertThat(authorizationService).isNotNull();
    }

    @Test
    void authorizeApprovedTransaction() {
        TransactionAuthorizationResponseType response =
                authorizationService.authorize(
                        "DE89370400440532013000",
                        BigDecimal.valueOf(100.00),
                        "EUR");

        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isNotEmpty();
        assertThat(response.getAuthorizationStatus())
                .isEqualTo(AuthorizationStatusType.APPROVED);
        assertThat(response.getAuthorizationCode()).startsWith("AUTH-");
    }

    @Test
    void authorizeDeclinedHighValueTransaction() {
        TransactionAuthorizationResponseType response =
                authorizationService.authorize(
                        "DE89370400440532013000",
                        BigDecimal.valueOf(50_000.00),
                        "EUR");

        assertThat(response.getAuthorizationStatus())
                .isEqualTo(AuthorizationStatusType.DECLINED);
        assertThat(response.getErrorMessage()).isNotBlank();
    }
}
