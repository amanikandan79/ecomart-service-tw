package uk.tw.energy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.PricePlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PricePlanServiceTest {

    private MeterReadingService meterReadingService;
    private PricePlanService pricePlanService;

    @BeforeEach
    public void setUp() {
        meterReadingService = new MeterReadingService(new java.util.HashMap<>());
        PricePlan pricePlan = new PricePlan("plan-1", null, BigDecimal.valueOf(1.0), null);
        pricePlanService = new PricePlanService(Arrays.asList(pricePlan), meterReadingService);
    }

    @Test
    public void calculateCost_shouldMultiplyAveragePowerByHours() {
        String smartMeterId = "meter-1";
        ElectricityReading r1 = new ElectricityReading(Instant.now().minusSeconds(3600), BigDecimal.valueOf(2.0));
        ElectricityReading r2 = new ElectricityReading(Instant.now(), BigDecimal.valueOf(2.0));
        meterReadingService.storeReadings(smartMeterId, Arrays.asList(r1, r2));

        var opt = pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);
        assertThat(opt).isPresent();
        var map = opt.get();
        BigDecimal cost = map.get("plan-1");
        assertThat(cost).isEqualByComparingTo(BigDecimal.valueOf(2.0).setScale(2));
    }

    @Test
    public void calculateCost_shouldReturnZeroForInsufficientReadings() {
        String smartMeterId = "meter-2";
        ElectricityReading r1 = new ElectricityReading(Instant.now(), BigDecimal.valueOf(2.0));
        meterReadingService.storeReadings(smartMeterId, Arrays.asList(r1));

        var opt = pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);
        assertThat(opt).isPresent();
        var map = opt.get();
        BigDecimal cost = map.get("plan-1");
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    public void calculateCost_shouldHandleSmallReadingsWithoutRoundingToZero() {
        String smartMeterId = "meter-3";
        ElectricityReading r1 = new ElectricityReading(Instant.now().minusSeconds(3600), BigDecimal.valueOf(0.05));
        ElectricityReading r2 = new ElectricityReading(Instant.now(), BigDecimal.valueOf(0.05));
        meterReadingService.storeReadings(smartMeterId, Arrays.asList(r1, r2));

        var opt = pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);
        assertThat(opt).isPresent();
        var map = opt.get();
        BigDecimal cost = map.get("plan-1");
        // energy = 0.05 kW * 1h = 0.05 kWh -> cost 0.05 -> rounded to 0.1 with scale 1
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.05"));
    }
}
