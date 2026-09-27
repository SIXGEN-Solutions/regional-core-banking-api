package com.regional.corebanking.customer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.customer.application.exception.CustomerNotFoundException;
import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import com.regional.corebanking.customer.domain.CustomerVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerApiHttpContractTest {

    private static final String CORRELATION_ID =
            "11111111-1111-1111-1111-111111111111";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var adapter = new CustomerApiAdapter(new StubUseCase(), new CustomerApiMapper(objectMapper));
        mvc = MockMvcBuilders.standaloneSetup(adapter)
                .setControllerAdvice(new CustomerApiExceptionHandler())
                .build();
    }

    @Test
    void searchesCustomerByNiu() throws Exception {
        mvc.perform(get("/api/v1/customers")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .queryParam("financialInstitutionCode", "REGIONAL")
                        .queryParam("niu", "NIU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.customers[0].customerReference").value("CUS-001"))
                .andExpect(jsonPath("$.customers[0].niu").value("NIU-001"));
    }

    @Test
    void getsCustomerByCustomerReference() throws Exception {
        mvc.perform(get("/api/v1/customers/CUS-001")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("X-Financial-Institution-Code", "REGIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("CUS-001"))
                .andExpect(jsonPath("$.niu").value("NIU-001"))
                .andExpect(jsonPath("$.source").value("AMPLITUDE"));
    }

    @Test
    void keepsCurrentCompositeVerificationRequestSignature() throws Exception {
        String body = """
                {
                  "accountReference": "001-123456-7",
                  "expectedNiu": "NIU-001",
                  "expectedAccountHolder": "Jane Doe",
                  "requiredKycFields": ["niu", "legalName", "phoneNumber", "email"],
                  "requestedAt": "2026-09-13T00:00:00Z"
                }
                """;

        mvc.perform(post("/api/v1/customer-verifications")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("INDETERMINATE"))
                .andExpect(jsonPath("$.customerReference").value("CUS-001"))
                .andExpect(jsonPath("$.accountReference").value("001-123456-7"))
                .andExpect(jsonPath("$.source").value("AMPLITUDE"));
    }

    @Test
    void mapsMissingCustomerTo404Problem() throws Exception {
        mvc.perform(get("/api/v1/customers/MISSING")
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .header("X-Financial-Institution-Code", "REGIONAL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.code").value("ERROR"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    private static final class StubUseCase implements CustomerVerificationUseCase {
        @Override
        public List<CustomerSummary> searchCustomers(String financialInstitutionCode, String niu, String customerNumber) {
            return List.of(new CustomerSummary("CUS-001", "CUS-001", "REGIONAL", "NIU-001", "Jane Doe"));
        }

        @Override
        public CustomerIdentity getCustomer(String financialInstitutionCode, String customerReference) {
            if ("MISSING".equals(customerReference)) {
                throw new CustomerNotFoundException(customerReference);
            }
            return identity();
        }

        @Override
        public List<BankAccount> getCustomerAccounts(String financialInstitutionCode, String customerReference, String rib, String iban) {
            return List.of(account());
        }

        @Override
        public CustomerVerification.Result verifyCustomer(CustomerVerification.Request request) {
            Instant now = Instant.parse("2026-09-13T00:00:00Z");
            return new CustomerVerification.Result(
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    now,
                    CustomerVerification.Outcome.INDETERMINATE,
                    "CUS-001",
                    "001-123456-7",
                    List.of(new CustomerVerification.Check(
                            CustomerVerification.CheckType.REQUIRED_KYC_VERIFIED,
                            CustomerVerification.CheckResult.UNKNOWN,
                            null,
                            now)),
                    identity(),
                    account());
        }

        private static CustomerIdentity identity() {
            Instant now = Instant.parse("2026-09-13T00:00:00Z");
            return new CustomerIdentity(
                    "CUS-001", "CUS-001", "REGIONAL", "NIU-001", "Jane Doe",
                    "+237600000000", "jane@example.com", CustomerIdentity.KycStatus.UNKNOWN,
                    List.of(
                            new CustomerIdentity.KycField("niu", "NIU-001", true, null, null),
                            new CustomerIdentity.KycField("legalName", "Jane Doe", true, null, null),
                            new CustomerIdentity.KycField("phoneNumber", "+237600000000", true, null, null),
                            new CustomerIdentity.KycField("email", "jane@example.com", true, null, null)),
                    null, now);
        }

        private static BankAccount account() {
            return new BankAccount(
                    "001-123456-7", "CUS-001", "REGIONAL", "**********56-7", "001",
                    BankAccount.AccountType.UNKNOWN, BankAccount.AccountStatus.ACTIVE,
                    Set.of(), Instant.parse("2026-09-13T00:00:00Z"));
        }
    }
}
