package uk.tw.energy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.tw.energy.App;
import uk.tw.energy.builders.MeterReadingsBuilder;
import uk.tw.energy.domain.MeterReadings;

import java.util.Collections;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class)
public class EndpointIT {

    private static final String KNOWN_SMART_METER_ID = "smart-meter-0";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper mapper;

    @Test
    public void shouldStoreReadings() throws JsonProcessingException {
        MeterReadings meterReadings = new MeterReadingsBuilder()
                .setSmartMeterId(KNOWN_SMART_METER_ID)
                .generateElectricityReadings()
                .build();
        HttpEntity<String> entity = getStringHttpEntity(meterReadings);

        ResponseEntity<String> response = restTemplate.postForEntity("/readings/store", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldRejectInvalidReadings() throws JsonProcessingException {
        MeterReadings meterReadings = new MeterReadings(null, Collections.emptyList());
        HttpEntity<String> entity = getStringHttpEntity(meterReadings);

        ResponseEntity<String> response = restTemplate.postForEntity("/readings/store", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldRejectUnknownMeterReadings() throws JsonProcessingException {
        MeterReadings meterReadings = new MeterReadingsBuilder()
                .setSmartMeterId("unknown-meter-id")
                .generateElectricityReadings()
                .build();
        HttpEntity<String> entity = getStringHttpEntity(meterReadings);

        ResponseEntity<String> response = restTemplate.postForEntity("/readings/store", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void givenMeterIdShouldReturnAMeterReadingAssociatedWithMeterId() throws JsonProcessingException {
        String smartMeterId = KNOWN_SMART_METER_ID;
        populateMeterReadingsForMeter(smartMeterId);

        ResponseEntity<String> response = restTemplate.getForEntity("/readings/read/" + smartMeterId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldCalculateAllPrices() throws JsonProcessingException {
        String smartMeterId = KNOWN_SMART_METER_ID;
        populateMeterReadingsForMeter(smartMeterId);

        ResponseEntity<String> response = restTemplate.getForEntity("/price-plans/compare-all/" + smartMeterId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void givenMeterIdAndLimitShouldReturnRecommendedCheapestPricePlans() throws JsonProcessingException {
        String smartMeterId = KNOWN_SMART_METER_ID;
        populateMeterReadingsForMeter(smartMeterId);

        ResponseEntity<String> response =
                restTemplate.getForEntity("/price-plans/recommend/" + smartMeterId + "?limit=2", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpEntity<String> getStringHttpEntity(Object object) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String jsonMeterData = mapper.writeValueAsString(object);
        return (HttpEntity<String>) new HttpEntity(jsonMeterData, headers);
    }

    private void populateMeterReadingsForMeter(String smartMeterId) throws JsonProcessingException {
        MeterReadings readings = new MeterReadingsBuilder().setSmartMeterId(smartMeterId)
                .generateElectricityReadings(20)
                .build();

        HttpEntity<String> entity = getStringHttpEntity(readings);
        restTemplate.postForEntity("/readings/store", entity, String.class);
    }
}
