package com.xperp.partners;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"PARTNERS_API_TOKEN=test-token", "WEBHOOK_SECRET=test-secret"})
class PartnersMockApplicationTests {

  @Test
  void contextLoads() {}
}
