package com.vadimevteev.aiincidentassistant.service;

import com.vadimevteev.aiincidentassistant.model.ParsedIncident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PiiScrubber {

    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{1,4}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.+\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}\\b");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    // Russian internal passport: keyword + 4-digit series + 6-digit number (space optional)
    private static final Pattern PASSPORT_RU_CONTEXT_PATTERN = Pattern.compile("(?i)\\b(?:паспорт|passport)\\s*(?:серия|series)?\\s*:?\\s*\\d{4}\\s?\\d{6}\\b");
    // International passport: keyword + letter prefix + 6–8 digits (e.g. E12345678)
    private static final Pattern PASSPORT_INTL_CONTEXT_PATTERN = Pattern.compile("(?i)\\bpassport\\s*(?:no\\.?|number|#)?\\s*:?\\s*[A-Z]\\d{6,8}\\b");
    // Generic numeric passport: keyword + 8–9 digit number (US and others)
    private static final Pattern PASSPORT_NUMERIC_CONTEXT_PATTERN = Pattern.compile("(?i)\\b(?:паспорт|passport)\\s*(?:no\\.?|number)?\\s*:?\\s*\\d{8,9}\\b");
    // Russian internal passport without keyword: mandatory space between series and number reduces false positives
    private static final Pattern PASSPORT_RU_STANDALONE_PATTERN = Pattern.compile("\\b\\d{4}\\s\\d{6}\\b");
    // US Social Security Number: xxx-xx-xxxx
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\+?[\\d][\\d\\s\\-().]{6,14}[\\d]\\b");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("\\b(user_id|account_id|user|account|customer_id)\\s*[:=]\\s*\\d+");

    public ParsedIncident scrub(ParsedIncident incident) {
        if (incident.normalizedDescription() == null) {
            return incident;
        }

        AtomicInteger maskedTokens = new AtomicInteger();
        String scrubbedDescription = incident.normalizedDescription();

        scrubbedDescription = replace(scrubbedDescription, CARD_NUMBER_PATTERN, "[CARD_NUMBER]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, EMAIL_PATTERN, "[EMAIL]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, IP_ADDRESS_PATTERN, "[IP_ADDRESS]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, PASSPORT_RU_CONTEXT_PATTERN, "[PASSPORT_NUMBER]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, PASSPORT_INTL_CONTEXT_PATTERN, "[PASSPORT_NUMBER]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, PASSPORT_NUMERIC_CONTEXT_PATTERN, "[PASSPORT_NUMBER]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, PASSPORT_RU_STANDALONE_PATTERN, "[PASSPORT_NUMBER]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, SSN_PATTERN, "[SSN]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, USER_ID_PATTERN, "[USER_ID]", maskedTokens);
        scrubbedDescription = replace(scrubbedDescription, PHONE_PATTERN, "[PHONE]", maskedTokens);

        int count = maskedTokens.get();
        if (count > 0) {
            log.info("PII scrubbing applied: {} tokens masked", count);
        }

        return new ParsedIncident(scrubbedDescription, incident.keywords());
    }

    private String replace(String value, Pattern pattern, String replacement, AtomicInteger maskedTokens) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            maskedTokens.incrementAndGet();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
