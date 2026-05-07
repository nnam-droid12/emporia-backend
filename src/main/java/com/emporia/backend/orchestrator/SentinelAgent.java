package com.emporia.backend.orchestrator;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.mcp.McpToolset;
import io.modelcontextprotocol.client.transport.ServerParameters;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SentinelAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final McpToolset mcpToolset;

    public SentinelAgent(@Value("${nokia.network-as-code.api-key}") String rapidApiKey) {

        String npxCommand = System.getProperty("os.name").toLowerCase().contains("win") ? "npx.cmd" : "npx";

        ServerParameters serverParams = ServerParameters.builder(npxCommand)
                .args(List.of(
                        "-y",
                        "mcp-remote", "https://mcp-eu.rapidapi.com",
                        "--header", "x-api-host: network-as-code.nokia.rapidapi.com",
                        "--header", "x-api-key: " + rapidApiKey
                ))
                .build();

        this.mcpToolset = new McpToolset(serverParams);

        this.agent = LlmAgent.builder()
                .name("Emporia-Sentinel")
                .model("gemini-3.1-flash-lite-preview")
                .instruction("You are the final security checkpoint for an escrow payout. " +
                        "STEP 1: Call the Nokia SIM Swap tool exactly ONCE for the provided phone number. Set maxAge to 240 hours. " +
                        "STEP 2: Analyze the response. " +
                        "If a SIM swap occurred recently (swapped: true), you MUST output 'FRAUD_DETECTED | TRANSACTION_FROZEN'. " +
                        "If no swap occurred (swapped: false), output 'SAFE | PROCEED_PAYOUT'. " +
                        "Do not provide explanations, only the exact formatted string.")
                .tools(this.mcpToolset)
                .build();

        this.runner = new InMemoryRunner(this.agent, "emporia_security_app");
    }

    public String checkSimSwapRisk(String targetPhone) {
        String prompt = String.format("Run final fraud diagnostic for payout target: %s", targetPhone);

        Session session = runner.sessionService()
                .createSession("emporia_security_app", targetPhone)
                .blockingGet();

        Content userMessage = Content.fromParts(Part.fromText(prompt));
        Flowable<Event> eventStream = runner.runAsync(targetPhone, session.id(), userMessage);

        AtomicReference<String> finalDecision = new AtomicReference<>("");

        eventStream.blockingForEach(event -> {
            if (event.finalResponse()) {
                finalDecision.set(event.stringifyContent());
            }
        });

        return finalDecision.get();
    }

    @PreDestroy
    public void cleanup() {
        if (this.mcpToolset != null) {
            try { mcpToolset.close(); } catch (Exception ignored) {}
        }
    }
}