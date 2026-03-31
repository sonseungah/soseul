package com.soseul.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("/api/generate".equals(httpRequest.getRequestURI())
                && "POST".equalsIgnoreCase(httpRequest.getMethod())) {

            String ip = getClientIp(httpRequest);
            long now = System.currentTimeMillis();
            Deque<Long> times = requestTimes.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

            synchronized (times) {
                while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                    times.pollFirst();
                }
                if (times.size() >= MAX_REQUESTS) {
                    httpResponse.setStatus(429);
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.getWriter().write("{\"error\":\"요청이 너무 많습니다. 1분 후 다시 시도해 주세요.\"}");
                    return;
                }
                times.addLast(now);
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
