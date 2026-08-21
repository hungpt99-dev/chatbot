package com.helpdesk.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.domain.engine.ConversationSnapshot;
import com.helpdesk.domain.engine.LlmPort;
import com.helpdesk.domain.engine.LlmStepDecision;
import com.helpdesk.web.dto.SopResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible chat-completions LLM port (works with OpenAI, Azure OpenAI,
 * and most OpenAI-compatible gateways by changing {@code baseUrl}).
 *
 * <p>Discipline enforced at the prompt + parse level: the model is asked to return
 * strict JSON with an enumerated {@code branchKey} chosen ONLY from the current
 * step's options, and an {@code intent} of CONTINUE/RESOLVE/ESCALATE. The app still
 * re-validates the branch key against the step graph (see {@code ConversationService})
 * — the model is never trusted to set SOP state directly.
 *
 * <p>On any transport/parse failure the port returns {@code null} so the caller
 * degrades to the offline interpreter rather than failing the request.
 */
@org.springframework.stereotype.Component
public class OpenAiCompatibleLlmPort implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmPort.class);

    private final HelpdeskLlmProperties props;
    private final RestClient client;
    private final ObjectMapper mapper;

    public OpenAiCompatibleLlmPort(HelpdeskLlmProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public LlmStepDecision decide(ConversationSnapshot snapshot, String userMessage) {
        if (!isConfigured()) return null;
        try {
            String body = buildRequestBody(snapshot, userMessage);
            String raw = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(raw);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("LLM call failed; degrading to offline interpreter: {}", ex.getMessage());
            return null;
        }
    }

    private String buildRequestBody(ConversationSnapshot s, String userMessage) throws JsonProcessingException {
        String stepText = describeStep(s.currentStep());
        StringBuilder history = new StringBuilder();
        if (s.recentMessages() != null) {
            for (String m : s.recentMessages()) history.append("- ").append(m).append("\n");
        }
        String userPrompt = "SOP: " + s.sopTitle() + "\nCurrent step:\n" + stepText +
                "\nRecent conversation:\n" + history + "\nEmployee says: " + userMessage +
                "\nReturn strict JSON: {\"intent\":\"CONTINUE|RESOLVE|ESCALATE\"," +
                "\"branchKey\":\"<key from options or null>\",\"stepResult\":\"<note or null>\"," +
                "\"escalationReason\":\"<reason or null>\",\"response\":\"<reply to employee>\"}.";

        ChatRequest req = new ChatRequest(props.model(),
                List.of(new Msg("system", props.systemPrompt()), new Msg("user", userPrompt)),
                false);
        return mapper.writeValueAsString(req);
    }

    private static String describeStep(SopResponse.StepDto step) {
        if (step == null) return "(no current step)";
        StringBuilder sb = new StringBuilder();
        sb.append("stepKey=").append(step.stepKey()).append("\n");
        sb.append("instruction=").append(step.instruction()).append("\n");
        sb.append("terminal=").append(step.terminal()).append("\n");
        if (step.branches() != null && !step.branches().isEmpty()) {
            sb.append("branch options (branchKey: condition):\n");
            for (SopResponse.BranchDto b : step.branches()) {
                sb.append("  ").append(b.branchKey()).append(": ").append(b.conditionText()).append("\n");
            }
        } else {
            sb.append("branch options: none\n");
        }
        return sb.toString();
    }

    private LlmStepDecision parse(String raw) throws JsonProcessingException {
        if (raw == null) return null;
        JsonNode root = mapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null) return null;
        // The model may wrap JSON in markdown fences; strip them.
        String json = stripFences(content);
        JsonNode dec = mapper.readTree(json);
        String intent = str(dec, "intent");
        String branchKey = str(dec, "branchKey");
        String stepResult = str(dec, "stepResult");
        String escalationReason = str(dec, "escalationReason");
        String response = str(dec, "response");
        return new LlmStepDecision(blankToNull(intent), blankToNull(branchKey),
                blankToNull(stepResult), blankToNull(escalationReason), blankToNull(response));
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNL = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (firstNL > 0 && last > firstNL) {
                t = t.substring(firstNL + 1, last);
            }
        }
        return t.trim();
    }

    private static String str(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // ---- JSON request shape (Jackson) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatRequest(String model, List<Msg> messages,
                               @JsonProperty("temperature") double temperature) {
        ChatRequest(String model, List<Msg> messages, boolean stream) {
            this(model, messages, 0.2);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Msg(String role, String content) {}
}
