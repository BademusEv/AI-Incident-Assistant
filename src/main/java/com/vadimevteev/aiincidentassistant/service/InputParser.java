package com.vadimevteev.aiincidentassistant.service;

import com.vadimevteev.aiincidentassistant.model.ParsedIncident;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InputParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    public ParsedIncident parse(String description) {
        String normalized = description.strip().replaceAll("\\s+", " ");
        String lowerCase = normalized.toLowerCase(Locale.ROOT);

        Set<String> keywords = new TreeSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(lowerCase);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }

        return new ParsedIncident(normalized, Set.copyOf(keywords));
    }
}
