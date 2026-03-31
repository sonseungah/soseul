package com.soseul.controller;

import com.soseul.model.GenerateRequest;
import com.soseul.service.AnthropicService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class GenerateController {

    private final AnthropicService anthropicService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public GenerateController(AnthropicService anthropicService) {
        this.anthropicService = anthropicService;
    }

    @PostMapping(value = "/generate", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter generate(@RequestBody GenerateRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        executor.submit(() -> anthropicService.streamToEmitter(req, emitter));
        return emitter;
    }
}
