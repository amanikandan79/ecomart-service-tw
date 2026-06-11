package uk.tw.energy.controller;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.tw.energy.exception.InvalidMeterReadingException;
import uk.tw.energy.exception.MeterNotFoundException;
import uk.tw.energy.service.AccountService;
import uk.tw.energy.service.PricePlanService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/price-plans")
public class PricePlanComparatorController {

    public final static String PRICE_PLAN_ID_KEY = "pricePlanId";
    public final static String PRICE_PLAN_COMPARISONS_KEY = "pricePlanComparisons";
    private static final Logger logger = LoggerFactory.getLogger(PricePlanComparatorController.class);
    private final PricePlanService pricePlanService;
    private final AccountService accountService;

    public PricePlanComparatorController(PricePlanService pricePlanService, AccountService accountService) {
        this.pricePlanService = pricePlanService;
        this.accountService = accountService;
    }

    @GetMapping("/compare-all/{smartMeterId}")
    public ResponseEntity<Map<String, Object>> calculatedCostForEachPricePlan(@PathVariable String smartMeterId) {
        logger.info("Compare-all request received for meterId={}", smartMeterId);
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (pricePlanId == null) {
            logger.warn("Compare-all rejected for unknown meterId={}", smartMeterId);
            throw new MeterNotFoundException(smartMeterId);
        }
        Optional<Map<String, BigDecimal>> consumptionsForPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);

        if (!consumptionsForPricePlans.isPresent()) {
            logger.warn("Compare-all rejected because no readings found for meterId={}", smartMeterId);
            throw new MeterNotFoundException(smartMeterId);
        }

        Map<String, Object> pricePlanComparisons = new HashMap<>();
        pricePlanComparisons.put(PRICE_PLAN_ID_KEY, pricePlanId);
        pricePlanComparisons.put(PRICE_PLAN_COMPARISONS_KEY, consumptionsForPricePlans.get());

        logger.info("Compare-all response prepared for meterId={} with {} plan(s)", smartMeterId, consumptionsForPricePlans.get().size());
        return ResponseEntity.ok(pricePlanComparisons);
    }

    @GetMapping("/recommend/{smartMeterId}")
    public ResponseEntity<List<Map.Entry<String, BigDecimal>>> recommendCheapestPricePlans(@PathVariable String smartMeterId,
                                                                                           @RequestParam(value = "limit", required = false) Integer limit) {
        logger.info("Recommend request received for meterId={} limit={}", smartMeterId, limit);
        if (limit != null && limit <= 0) {
            throw new InvalidMeterReadingException("Request parameter limit must be greater than 0");
        }
        Optional<Map<String, BigDecimal>> consumptionsForPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);

        if (!consumptionsForPricePlans.isPresent()) {
            logger.warn("Recommend rejected because no readings found for meterId={}", smartMeterId);
            throw new MeterNotFoundException(smartMeterId);
        }

        List<Map.Entry<String, BigDecimal>> recommendations = new ArrayList<>(consumptionsForPricePlans.get().entrySet());
        
        recommendations.sort(
        	    Comparator.comparing((Map.Entry<String, BigDecimal> entry) -> entry.getValue())
        	);
        if (limit != null && limit < recommendations.size()) {
            recommendations = recommendations.subList(0, limit);
        }

        logger.info("Recommend response prepared for meterId={} with {} plan(s)", smartMeterId, recommendations.size());
        return ResponseEntity.ok(recommendations);
    }
}
