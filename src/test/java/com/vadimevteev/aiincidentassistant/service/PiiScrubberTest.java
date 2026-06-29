package com.vadimevteev.aiincidentassistant.service;

import com.vadimevteev.aiincidentassistant.model.ParsedIncident;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PiiScrubberTest {

    private final PiiScrubber piiScrubber = new PiiScrubber();

    @Test
    void masksEmailInIncidentText() {
        ParsedIncident incident = incident("Contact user john.doe@example.com about payment timeout.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[EMAIL]")
                .doesNotContain("john.doe@example.com");
    }

    @Test
    void masksCardNumberInIncidentText() {
        ParsedIncident incident = incident("Card 4111-1111-1111-1111 failed during checkout.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[CARD_NUMBER]")
                .doesNotContain("4111-1111-1111-1111");
    }

    @Test
    void masksIpAddressInIncidentText() {
        ParsedIncident incident = incident("Requests from 10.20.30.40 show auth token failures.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[IP_ADDRESS]")
                .doesNotContain("[PHONE]")
                .doesNotContain("10.20.30.40");
    }

    @Test
    void masksPhoneInIncidentText() {
        ParsedIncident incident = incident("Customer phone +1 (555) 123-4567 reports payment timeout.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[PHONE]")
                .doesNotContain("+1 (555) 123-4567");
    }

    @Test
    void masksUserIdInIncidentText() {
        ParsedIncident incident = incident("Customer user_id=12345 reports payment failure.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[USER_ID]")
                .doesNotContain("user_id=12345");
    }

    @Test
    void returnsIncidentWhenNormalizedDescriptionIsNull() {
        ParsedIncident incident = new ParsedIncident(null, Set.of("payment"));

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed).isSameAs(incident);
    }

    @Test
    void doesNotMaskRetrievalKeywords() {
        ParsedIncident incident = incident("Payment card timeout affects checkout.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("Payment")
                .contains("card")
                .contains("timeout");
    }

    @Test
    void textWithoutPiiIsReturnedUnchanged() {
        ParsedIncident incident = incident("Payment timeout affects checkout.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription()).isEqualTo(incident.normalizedDescription());
    }

    @Test
    void keywordsRemainTheSameAfterScrubbing() {
        ParsedIncident incident = incident("Payment card timeout for john.doe@example.com and 4111111111111111.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.keywords()).isEqualTo(incident.keywords());
    }

    @Test
    void masksRussianPassportWithContextKeyword() {
        ParsedIncident incident = incident("Customer паспорт 4515 123456 cannot complete payment.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[PASSPORT_NUMBER]")
                .doesNotContain("4515 123456");
    }

    @Test
    void masksRussianPassportWithEnglishKeyword() {
        ParsedIncident incident = incident("User passport 4515123456 is blocked.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[PASSPORT_NUMBER]")
                .doesNotContain("4515123456");
    }

    @Test
    void masksInternationalPassportWithLetterPrefix() {
        ParsedIncident incident = incident("Customer passport E12345678 failed KYC check.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[PASSPORT_NUMBER]")
                .doesNotContain("E12345678");
    }

    @Test
    void masksRussianPassportStandaloneFormat() {
        ParsedIncident incident = incident("Document 4515 123456 was submitted for verification.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[PASSPORT_NUMBER]")
                .doesNotContain("4515 123456");
    }

    @Test
    void masksSsnWithHyphenFormat() {
        ParsedIncident incident = incident("SSN 123-45-6789 appeared in payment error log.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[SSN]")
                .doesNotContain("123-45-6789");
    }

    @Test
    void doesNotMaskCardNumberAsPassport() {
        ParsedIncident incident = incident("Card 4111-1111-1111-1111 declined at checkout.");

        ParsedIncident scrubbed = piiScrubber.scrub(incident);

        assertThat(scrubbed.normalizedDescription())
                .contains("[CARD_NUMBER]")
                .doesNotContain("[PASSPORT_NUMBER]");
    }

    private ParsedIncident incident(String description) {
        return new InputParser().parse(description);
    }
}
