package uk.tw.energy.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import uk.tw.energy.builders.MeterReadingsBuilder;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.MeterReadings;
import uk.tw.energy.exception.InvalidMeterReadingException;
import uk.tw.energy.exception.MeterNotFoundException;
import uk.tw.energy.service.AccountService;
import uk.tw.energy.service.MeterReadingService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MeterReadingControllerTest {

    private static final String SMART_METER_ID = "10101010";
    private MeterReadingController meterReadingController;
    private MeterReadingService meterReadingService;

    @BeforeEach
    public void setUp() {
        this.meterReadingService = new MeterReadingService(new HashMap<>());
        HashMap<String, String> meterAccounts = new HashMap<>();
        meterAccounts.put(SMART_METER_ID, "price-plan-1");
        meterAccounts.put("00001", "price-plan-2");
        this.meterReadingController = new MeterReadingController(
                meterReadingService,
                new AccountService(meterAccounts),
                meterReadingsValidator()
        );
    }

    private SmartValidator meterReadingsValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    @Test
    public void givenNoMeterIdIsSuppliedWhenStoringShouldThrowInvalidMeterReadingException() {
        MeterReadings meterReadings = new MeterReadings(null, Collections.emptyList());
        assertThrows(InvalidMeterReadingException.class, () -> meterReadingController.storeReadings(meterReadings));
    }

    @Test
    public void givenEmptyMeterReadingShouldThrowInvalidMeterReadingException() {
        MeterReadings meterReadings = new MeterReadings(SMART_METER_ID, Collections.emptyList());
        assertThrows(InvalidMeterReadingException.class, () -> meterReadingController.storeReadings(meterReadings));
    }

    @Test
    public void givenNullReadingsAreSuppliedWhenStoringShouldThrowInvalidMeterReadingException() {
        MeterReadings meterReadings = new MeterReadings(SMART_METER_ID, null);
        assertThrows(InvalidMeterReadingException.class, () -> meterReadingController.storeReadings(meterReadings));
    }

    @Test
    public void givenMultipleBatchesOfMeterReadingsShouldStore() {
        MeterReadings meterReadings = new MeterReadingsBuilder().setSmartMeterId(SMART_METER_ID)
                .generateElectricityReadings()
                .build();

        MeterReadings otherMeterReadings = new MeterReadingsBuilder().setSmartMeterId(SMART_METER_ID)
                .generateElectricityReadings()
                .build();

        meterReadingController.storeReadings(meterReadings);
        meterReadingController.storeReadings(otherMeterReadings);

        List<ElectricityReading> expectedElectricityReadings = new ArrayList<>();
        expectedElectricityReadings.addAll(meterReadings.electricityReadings());
        expectedElectricityReadings.addAll(otherMeterReadings.electricityReadings());

        assertThat(meterReadingService.getReadings(SMART_METER_ID).get()).isEqualTo(expectedElectricityReadings);
    }

    @Test
    public void givenMeterReadingsAssociatedWithTheUserShouldStoreAssociatedWithUser() {
        MeterReadings meterReadings = new MeterReadingsBuilder().setSmartMeterId(SMART_METER_ID)
                .generateElectricityReadings()
                .build();

        MeterReadings otherMeterReadings = new MeterReadingsBuilder().setSmartMeterId("00001")
                .generateElectricityReadings()
                .build();

        meterReadingController.storeReadings(meterReadings);
        meterReadingController.storeReadings(otherMeterReadings);

        assertThat(meterReadingService.getReadings(SMART_METER_ID).get()).isEqualTo(meterReadings.electricityReadings());
    }

    @Test
    public void givenMeterIdThatIsNotRecognisedShouldThrowMeterNotFoundException() {
        assertThrows(MeterNotFoundException.class, () -> meterReadingController.readReadings(SMART_METER_ID));
    }

    @Test
    public void givenUnknownMeterIdWhenStoringShouldThrowMeterNotFoundException() {
        MeterReadings meterReadings = new MeterReadingsBuilder().setSmartMeterId("unknown-id")
                .generateElectricityReadings()
                .build();
        assertThrows(MeterNotFoundException.class, () -> meterReadingController.storeReadings(meterReadings));
    }

    @Test
    public void givenSpecialCharactersInMeterIdShouldThrowInvalidMeterReadingException() {
        MeterReadings meterReadings = new MeterReadings("smart-meter-0'; DROP--", Collections.emptyList());
        assertThrows(InvalidMeterReadingException.class, () -> meterReadingController.storeReadings(meterReadings));
    }

    @Test
    public void givenMeterIdWithSpacesShouldThrowInvalidMeterReadingException() {
        MeterReadings meterReadings = new MeterReadings("smart meter 0", Collections.emptyList());
        assertThrows(InvalidMeterReadingException.class, () -> meterReadingController.storeReadings(meterReadings));
    }

    @Test
    public void givenValidAlphanumericMeterIdWithHyphensShouldSucceed() {
        MeterReadings meterReadings = new MeterReadingsBuilder()
                .setSmartMeterId(SMART_METER_ID)
                .generateElectricityReadings()
                .build();
        meterReadingController.storeReadings(meterReadings);
        assertThat(meterReadingService.getReadings(SMART_METER_ID)).isPresent();
    }
}
