package com.emporia.backend.orchestrator;

import com.emporia.backend.mcp.NokiaCamaraTools;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class GatekeeperAgent {

    private final LlmAgent agent;
    private final InMemoryRunner runner;

    public GatekeeperAgent(NokiaCamaraTools nokiaTools) {

        this.agent = LlmAgent.builder()
                .name("Emporia-Gatekeeper")
                .model("gemini-3.1-flash-lite-preview")
                .instruction("You are a strict security agent for a B2B escrow platform. " +
                        "Your sole job is to verify user identities before allowing them to create an account. " +
                        "You MUST use the 'verifyKycMatch' tool to check the user's details against telecom records. " +
                        "If the tool returns true, respond with the exact word 'APPROVED'. " +
                        "If the tool returns false, respond with the exact word 'REJECTED'.")
                .tools(nokiaTools.getKycTool())
                .build();

        this.runner = new InMemoryRunner(this.agent, "emporia_auth_app");
    }

    public boolean evaluateIdentity(String phoneNumber, String businessName) {
        String prompt = String.format("A new user is trying to register. Phone: %s, Claimed Business Name: %s. Please verify them.", phoneNumber, businessName);

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

        return finalDecision.get().contains("APPROVED");
    }
}