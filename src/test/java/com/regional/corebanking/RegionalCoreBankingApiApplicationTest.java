package com.regional.corebanking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "regional.confirmation.persistence=memory"
})
class RegionalCoreBankingApiApplicationTest {

    @Test
    void contextLoads() {
    }
}
