package com.vadimevteev.aiincidentassistant.ai;

import com.vadimevteev.aiincidentassistant.exception.AiProviderException;
import com.vadimevteev.aiincidentassistant.exception.InvalidAiResponseException;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.api.common.OpenAiApiClientErrorException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class SpringAiIncidentAiClient implements IncidentAiClient {

    private final ChatClient chatClient;

    public SpringAiIncidentAiClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public IncidentAnalysis analyze(IncidentPrompt prompt) {
        long startedAt = System.nanoTime();
        try {
            IncidentAnalysis analysis = chatClient.prompt()
                    .system(prompt.systemMessage())
                    .user(prompt.userMessage())
                    .call()
                    .entity(IncidentAnalysis.class);
            log.info("LLM incident analysis completed in {} ms", elapsedMillis(startedAt));
            return analysis;
        } catch (TransientAiException | NonTransientAiException | OpenAiApiClientErrorException | RestClientException e) {
            log.warn("LLM incident analysis failed after {} ms", elapsedMillis(startedAt));
            throw new AiProviderException("AI provider failed to return a valid incident analysis", e);
        } catch (RuntimeException e) {
            log.warn("LLM incident analysis returned invalid structured output after {} ms", elapsedMillis(startedAt));
            throw new InvalidAiResponseException(
                    "AI response could not be converted to IncidentAnalysis",
                    "AI_OUTPUT_CONVERSION_FAILED"
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
