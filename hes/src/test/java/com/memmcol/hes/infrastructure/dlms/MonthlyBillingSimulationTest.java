package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.domain.profile.MetersLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(FakeDlmsReaderUtils.class)
public class MonthlyBillingSimulationTest {
    @Autowired
    private MetersLockService metersLockService;

    @Autowired
    private FakeDlmsReaderUtils fake;

    @Test
    void simulateMonthlyBillingRead_multipleRows() throws Exception {
        // Run one full polling pass
        metersLockService.readMonthlyBillWithLock("MMX-313-CT","202006001314","0.0.98.1.0.255", 50);

        // Print all decoded rows
        System.out.println("Decoded rows used for simulation:");
        fake.readRange("MMX","202006001314","0.0.98.1.0.255",null,null,null,false)
                .forEach(System.out::println);
    }
}

