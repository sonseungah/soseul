package com.soseul.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soseul.model.GenerateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AnthropicService {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public void streamToEmitter(GenerateRequest req, SseEmitter emitter) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                emitter.send(SseEmitter.event().name("error")
                        .data("API 키가 설정되지 않았습니다. " +
                              "환경 변수 ANTHROPIC_API_KEY를 설정하거나 " +
                              "application.properties에 직접 입력해 주세요."));
                emitter.complete();
                return;
            }

            String prompt = buildPrompt(req);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes());
                String errorMsg = parseAnthropicError(errorBody);
                emitter.send(SseEmitter.event().name("error").data(errorMsg));
                emitter.complete();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) continue;
                    try {
                        JsonNode node = mapper.readTree(data);
                        String type = node.path("type").asText();
                        if ("content_block_delta".equals(type)) {
                            String text = node.path("delta").path("text").asText("");
                            if (!text.isEmpty()) {
                                emitter.send(SseEmitter.event()
                                        .name("delta").data(text));
                            }
                        } else if ("message_stop".equals(type)) {
                            emitter.send(SseEmitter.event().name("done").data(""));
                            emitter.complete();
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }
            emitter.complete();

        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("서버 오류: " + e.getMessage()));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
        }
    }

    private String parseAnthropicError(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            String msg = node.path("error").path("message").asText("");
            return msg.isBlank() ? body : msg;
        } catch (Exception e) {
            return body;
        }
    }

    private String buildPrompt(GenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 전문 소설 작가입니다. 아래 정보를 바탕으로 소설의 **전체 개요**와 **앞부분(도입부)**을 작성해 주세요.\n");
        sb.append("한국어 맞춤법과 띄어쓰기 규칙을 반드시 준수하세요.\n\n");
        sb.append("## 소설 정보\n");
        sb.append("- 장르: ").append(req.getGenre()).append("\n");
        if (req.getSetting() != null && !req.getSetting().isBlank()) {
            sb.append("- 배경(장소/시대): ").append(req.getSetting()).append("\n");
        }
        sb.append("- 등장인물:\n").append(req.getCharacters()).append("\n");
        sb.append("- 핵심 사건/플롯:\n").append(req.getEvents()).append("\n");
        if (req.getConditions() != null && !req.getConditions().isBlank()) {
            sb.append("- 추가 조건/요청:\n").append(req.getConditions()).append("\n");
        }
        sb.append("\n## 작성 형식\n\n");
        sb.append("먼저 **전체 개요**를 작성해 주세요. 개요에는 다음을 포함하세요:\n");
        sb.append("- 핵심 세계관 / 배경 설명\n");
        sb.append("- 등장인물 소개 및 관계도\n");
        sb.append("- 전체 스토리 흐름 (발단 → 전개 → 절정 → 결말 방향)\n");
        sb.append("- 주요 갈등 구조\n");
        sb.append("- 키워드 및 테마\n\n");
        sb.append("개요 작성이 끝나면 반드시 아래 구분자를 정확히 출력하세요 (한 줄, 다른 텍스트 없이):\n");
        sb.append("===OPENING_START===\n\n");
        sb.append("그 다음 **소설 앞부분(도입부)**을 작성해 주세요:\n");
        sb.append("- 분량: 약 ").append(req.getLength()).append("자\n");
        sb.append("- 독자를 단숨에 몰입시키는 강렬한 첫 장면으로 시작\n");
        sb.append("- 장르 특성에 맞는 문체와 분위기\n");
        sb.append("- 자연스럽게 세계관과 인물을 소개\n");
        sb.append("- 다음 내용이 궁금하게 만드는 훅(hook)으로 마무리\n");
        return sb.toString();
    }
}
