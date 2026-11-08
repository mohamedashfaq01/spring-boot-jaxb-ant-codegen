package de.consorsbank.consumingwebservice.service;

import de.consorsbank.consumingwebservice.wsdl.AuthorizationStatusType;
import de.consorsbank.consumingwebservice.wsdl.TransactionAuthorizationRequestType;
import de.consorsbank.consumingwebservice.wsdl.TransactionAuthorizationResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.UUID;

/**
 * AuthorizationService demonstrates that the JAXB-generated classes
 * (produced by the {@code genJaxb} Gradle task via Apache Ant → XJC) are
 * fully usable within application business logic.
 *
 * <p><b>Important JAXB naming convention note:</b><br>
 * XJC maps XSD {@code complexType} definitions to Java classes by appending
 * the word "Type" to the complex-type name. So the XSD type
 * {@code TransactionAuthorizationRequestType} maps to the Java class
 * {@code TransactionAuthorizationRequestType}. The global XSD
 * {@code element} declarations (e.g. {@code TransactionAuthorizationRequest})
 * are exposed through {@code ObjectFactory} factory methods that return
 * {@code JAXBElement<TransactionAuthorizationRequestType>}. For plain
 * data-binding usage we work directly with the {@code *Type} classes.</p>
 *
 * <p>The imports at the top of this file reference classes that do NOT exist
 * in the hand-written {@code src/main/java} tree. They only exist after the
 * {@code genJaxb} task has run and XJC has written them to
 * {@code build/generated-sources/jaxb}. If those classes were absent or
 * failed to compile, this service would itself fail to compile — providing a
 * clear validation signal that the JAXB pipeline is working end-to-end.</p>
 */
@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    /**
     * Builds a {@link TransactionAuthorizationRequestType} from raw parameters,
     * simulates authorization, and returns a {@link TransactionAuthorizationResponseType}.
     *
     * @param accountNumber payer account / IBAN
     * @param amount        transaction amount
     * @param currency      ISO 4217 currency code (e.g. "EUR")
     * @return authorization response with status APPROVED or DECLINED
     */
    public TransactionAuthorizationResponseType authorize(
            String accountNumber,
            BigDecimal amount,
            String currency) {

        // ── 1. Build the JAXB request object ────────────────────────────────
        //    TransactionAuthorizationRequestType is a JAXB-generated class.
        //    It was produced by XJC from the XSD and compiled alongside this
        //    service class by Gradle's compileJava task.
        TransactionAuthorizationRequestType request = new TransactionAuthorizationRequestType();
        request.setTransactionId(UUID.randomUUID().toString());
        request.setAccountNumber(accountNumber);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setRequestTimestamp(nowAsXmlGregorianCalendar());

        log.info("Authorizing transaction [id={}] for account={} amount={} {}",
                request.getTransactionId(), request.getAccountNumber(),
                request.getAmount(), request.getCurrency());

        // ── 2. Simulate ESB call and build JAXB response ─────────────────────
        //    In production this would marshal the request to SOAP XML and
        //    send it to the ESB; here we just mock the decision logic.
        TransactionAuthorizationResponseType response = new TransactionAuthorizationResponseType();
        response.setTransactionId(request.getTransactionId());
        response.setResponseTimestamp(nowAsXmlGregorianCalendar());

        if (amount != null && amount.compareTo(BigDecimal.valueOf(10_000)) > 0) {
            // Simulate a declined high-value transaction
            response.setAuthorizationStatus(AuthorizationStatusType.DECLINED);
            response.setErrorMessage("Amount exceeds per-transaction limit");
            log.warn("Transaction {} DECLINED — amount {} exceeds limit",
                    request.getTransactionId(), amount);
        } else {
            response.setAuthorizationStatus(AuthorizationStatusType.APPROVED);
            response.setAuthorizationCode(
                    "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            log.info("Transaction {} APPROVED, auth code={}",
                    request.getTransactionId(), response.getAuthorizationCode());
        }

        return response;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private XMLGregorianCalendar nowAsXmlGregorianCalendar() {
        try {
            GregorianCalendar cal = GregorianCalendar.from(ZonedDateTime.now());
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create XMLGregorianCalendar", e);
        }
    }
}
