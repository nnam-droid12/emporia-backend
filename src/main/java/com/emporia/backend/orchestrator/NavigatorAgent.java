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
public class NavigatorAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final McpToolset mcpToolset;

    public NavigatorAgent(@Value("${nokia.network-as-code.api-key}") String rapidApiKey) {

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
                .name("Emporia-Navigator")
                .model("gemini-3.1-flash-lite-preview")
                .instruction("You are a strict logistics verification agent. " +
                        "STEP 1: Call the Nokia Location Verification tool exactly ONCE using the provided driver phone number and the target area coordinates (latitude, longitude, and a radius of 50000 meters). " +
                        "STEP 2: Read the network response. " +
                        "If the driver is verified to be within the delivery zone, you MUST immediately output 'ARRIVED'. " +
                        "If the driver is outside the zone, output 'IN_TRANSIT'. " +
                        "Do not loop or add conversational text.")
                .tools(this.mcpToolset)
                .build();

        this.runner = new InMemoryRunner(this.agent, "emporia_logistics_app");
    }

    public String verifyDeliveryLocation(String driverPhone, double targetLat, double targetLon) {
        String prompt = String.format("Verify logistics. Driver Phone: %s. Target Delivery Zone -> Lat: %f, Lon: %f.",
                driverPhone, targetLat, targetLon);

        Session session = runner.sessionService()
                .createSession("emporia_logistics_app", driverPhone)
                .blockingGet();

        Content userMessage = Content.fromParts(Part.fromText(prompt));
        Flowable<Event> eventStream = runner.runAsync(driverPhone, session.id(), userMessage);

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