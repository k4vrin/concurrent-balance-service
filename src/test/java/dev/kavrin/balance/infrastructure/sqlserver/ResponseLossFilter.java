package dev.kavrin.balance.infrastructure.sqlserver;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Replaces a completed application's original response with HTTP 503 only for explicitly marked test requests.
 *
 * <p>The actual controller and transaction run first; cached original bytes are observed but never
 * delivered to the client. This establishes recovery after a lost application response, not a TCP
 * partition. The filter is registered only in the optional test fault assembly.
 */
final class ResponseLossFilter implements Filter {
    private final ApplicationFaultPlan plan;
    ResponseLossFilter(ApplicationFaultPlan plan) { this.plan = plan; }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest http) || !(response instanceof HttpServletResponse output)
                || !"true".equals(http.getHeader("X-Test-Drop-Response"))) {
            chain.doFilter(request, response);
            return;
        }
        var cached = new ContentCachingResponseWrapper(output);
        chain.doFilter(request, cached);
        plan.droppedBody = cached.getContentAsByteArray();
        plan.droppedStatus = cached.getStatus();
        plan.droppedResponses.incrementAndGet();
        output.reset();
        output.setStatus(503);
        output.setContentType("application/problem+json");
        output.getOutputStream().write("{\"code\":\"TEST_RESPONSE_LOST\"}".getBytes(StandardCharsets.UTF_8));
    }
}
