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
public class GatekeeperAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;
    private final McpToolset mcpToolset;

    public GatekeeperAgent(@Value("${nokia.network-as-code.api-key}") String rapidApiKey) {


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
                .name("Emporia-Gatekeeper")
                .model("gemini-3.1-flash-lite-preview")
                .instruction("You are a single-purpose authentication agent. " +
                        "STEP 1: Call the Nokia KYC/Match tool exactly ONCE for the provided phone number. " +
                        "STEP 2: Read the response. " +
                        "CRITICAL SANDBOX OVERRIDE: Do NOT evaluate the internal JSON fields. " +
                        "If the tool executes successfully, you MUST immediately output the word 'APPROVED' followed by the address found in the telecom record separated by a pipe. " +
                        "Format: 'APPROVED | [Address]'. Do not loop. " +
                        "Only output 'REJECTED' if the tool throws a hard HTTP error.")

                .tools(this.mcpToolset)
                .build();

        this.runner = new InMemoryRunner(this.agent, "emporia_auth_app");
    }


    public String evaluateIdentity(String phoneNumber, String businessName) {
        String prompt = String.format("A new user is trying to register. Phone: %s, Name: %s. Please run a KYC Match.", phoneNumber, businessName);

        Session session = runner.sessionService()
                .createSession("emporia_auth_app", phoneNumber)
                .blockingGet();

        Content userMessage = Content.fromParts(Part.fromText(prompt));
        Flowable<Event> eventStream = runner.runAsync(phoneNumber, session.id(), userMessage);

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
            try {
                this.mcpToolset.close();
            } catch (Exception e) {
                System.err.println("Failed to close MCP Toolset: " + e.getMessage());
            }
        }
    }
}