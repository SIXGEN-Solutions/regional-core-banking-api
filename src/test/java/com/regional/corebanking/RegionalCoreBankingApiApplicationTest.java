package com.regional.corebanking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "regional.confirmation.persistence=memory",
        "regional.confirmation.hmac.active-key-version=test-v1",
        "regional.confirmation.hmac.keys=test-v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class RegionalCoreBankingApiApplicationTest {

    @Test
    void contextLoads() {
    }
}
