/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.wingman.acp;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.json.JacksonAcpJsonMapperSupplier;
import com.agentclientprotocol.sdk.spec.AcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import net.mcreator.io.OS;
import net.mcreator.ui.MCreator;
import net.mcreator.wingman.data.AssistantService;
import net.mcreator.wingman.data.ChatConversation;
import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.data.StreamListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assistant service backed by an external coding agent that speaks the Agent Client Protocol (ACP) over stdio.
 * The agent process is started lazily from the given command line, and one ACP session is kept per conversation.
 * <p>
 * The local history is the source of truth for what the user sees. The ACP session only carries the context of the
 * agent: it is reattached when possible, and when it is gone, a new session is started with the local history
 * relayed in its first prompt.
 */
public final class AcpChatService extends AssistantService {

    private static final Logger LOG = LogManager.getLogger(AcpChatService.class);

    // A single agent turn may run for a long time, so the SDK default request timeout is far too short
    private static final Duration REQUEST_TIMEOUT = Duration.ofHours(1);

    private final MCreator mcreator;
    private final AcpClientTransport acpClientTransport;

    private AcpSyncClient client;

    // Sessions the running agent process knows about (created or loaded since the process was started)
    private final Set<String> knownSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
    private final Map<String, Float> contextUsage = new ConcurrentHashMap<>();

    /**
     * @param agentCommandLine Command line that starts the ACP agent in stdio mode, e.g. "agent acp"
     */
    public AcpChatService(MCreator mcreator, String agentCommandLine) {
        this(mcreator, stdioTransport(parseCommandLine(agentCommandLine)));
    }

    public AcpChatService(MCreator mcreator, AcpClientTransport transport) {
        super(mcreator);
        this.mcreator = mcreator;
        this.acpClientTransport = transport;
    }

    private static AgentParameters parseCommandLine(String commandLine) {
        List<String> tokens = new ArrayList<>(Arrays.asList(commandLine.trim().split("\\s+")));
        if (tokens.isEmpty() || tokens.getFirst().isEmpty()) {
            throw new IllegalArgumentException("ACP agent command line must not be empty");
        }
        if (OS.getOS() == OS.WINDOWS) {
            // Go through the shell so .cmd shims of npm or Cursor installed agents resolve from PATH
            tokens.addAll(0, List.of("cmd.exe", "/c"));
        }
        return AgentParameters.builder(tokens.getFirst()).args(tokens.subList(1, tokens.size())).build();
    }

    private static AcpClientTransport stdioTransport(AgentParameters agentParameters) {
        StdioAcpClientTransport transport = new StdioAcpClientTransport(agentParameters,
                new JacksonAcpJsonMapperSupplier().get());
        transport.setStdErrorHandler(line -> LOG.debug("[agent] {}", line));
        transport.setExceptionHandler(e -> LOG.warn("ACP transport error", e));
        return transport;
    }

    private synchronized AcpSyncClient client() {
        if (client == null) {
            AcpSyncClient newClient = AcpClient.sync(acpClientTransport).requestTimeout(REQUEST_TIMEOUT).clientCapabilities(
                            new AcpSchema.ClientCapabilities(new AcpSchema.FileSystemCapability(false, false), false))
                    .sessionUpdateConsumer(this::handleSessionUpdate)
                    .requestPermissionHandler(this::handlePermissionRequest).build();
            try {
                newClient.initialize();
            } catch (RuntimeException e) {
                newClient.close();
                throw e;
            }
            client = newClient;
        }
        return client;
    }

    @Nullable
    private synchronized AcpSyncClient currentClient() {
        return client;
    }

    private String workspaceDirectory() {
        return mcreator.getWorkspace().getWorkspaceFolder().getAbsolutePath();
    }

    @Override
    public void stream(ChatConversation conversation, String messageText, StreamListener listener) {
        if (messageText == null || messageText.isBlank()) {
            return;
        }

        // Messages recorded before this request are relayed to the agent if the conversation gets a fresh session
        List<ChatMessage> history = conversation.messages();
        conversation.addMessage(ChatMessage.user(messageText));

        ActiveRequest active = new ActiveRequest(conversation, listener);
        String sessionId = null;
        try {
            AcpSyncClient client = client();
            Session session = ensureSession(client, conversation);
            sessionId = session.id();
            activeRequests.put(sessionId, active);
            // Instructions and the earlier history are sent only with the first prompt of a new session and are not
            // part of the local history
            String promptText = session.fresh() ? SessionPrompts.firstPrompt(history, messageText) : messageText;
            AcpSchema.PromptResponse response = client.prompt(
                    new AcpSchema.PromptRequest(sessionId, List.of(new AcpSchema.TextContent(promptText))));
            active.complete(response.stopReason());
        } catch (Throwable e) {
            // Errors (e.g. ServiceConfigurationError, NoClassDefFoundError) are reported too, so the UI never waits forever
            LOG.warn("ACP prompt failed", e);
            active.fail(e);
        } finally {
            if (sessionId != null) {
                activeRequests.remove(sessionId, active);
            }
        }
    }

    /**
     * @param id    ACP session id of the conversation
     * @param fresh true if the session was just created and has no history on the agent side
     */
    private record Session(String id, boolean fresh) {
    }

    private Session ensureSession(AcpSyncClient client, ChatConversation conversation) {
        String sessionId = conversation.sessionId();
        if (sessionId != null && knownSessions.contains(sessionId)) {
            return new Session(sessionId, false);
        }

        if (sessionId != null && client.getAgentCapabilities().supportsLoadSession()) {
            try {
                // The history the agent replays while loading is ignored, the local history is already shown
                client.loadSession(new AcpSchema.LoadSessionRequest(sessionId, workspaceDirectory(), List.of()));
                knownSessions.add(sessionId);
                return new Session(sessionId, false);
            } catch (RuntimeException e) {
                LOG.warn("Failed to load ACP session {}, starting a new one", sessionId, e);
            }
        }

        sessionId = client.newSession(new AcpSchema.NewSessionRequest(workspaceDirectory(), List.of())).sessionId();
        knownSessions.add(sessionId);
        conversation.setSessionId(sessionId);
        return new Session(sessionId, true);
    }

    @SuppressWarnings("resource")
    @Override
    public void cancel(ChatConversation conversation) {
        for (Map.Entry<String, ActiveRequest> entry : activeRequests.entrySet()) {
            ActiveRequest active = entry.getValue();
            if (active.conversation() == conversation) {
                active.cancel();
                AcpSyncClient client = currentClient();
                if (client != null) {
                    try {
                        client.cancel(new AcpSchema.CancelNotification(entry.getKey()));
                    } catch (RuntimeException e) {
                        LOG.warn("Failed to send ACP cancel notification", e);
                    }
                }
                return;
            }
        }
    }

    @Override
    public float getContextUsage(ChatConversation conversation) {
        String sessionId = conversation.sessionId();
        return sessionId == null ? 0f : contextUsage.getOrDefault(sessionId, 0f);
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            try {
                client.closeGracefully();
            } catch (RuntimeException e) {
                LOG.warn("Failed to close ACP client", e);
            }
            client = null;
        }
    }

    private void handleSessionUpdate(AcpSchema.SessionNotification notification) {
        AcpSchema.SessionUpdate update = notification.update();
        if (update instanceof AcpSchema.UsageUpdate usage) {
            if (usage.used() != null && usage.size() != null && usage.size() > 0) {
                contextUsage.put(notification.sessionId(), Math.min(1f, (float) usage.used() / usage.size()));
            }
            return;
        }

        // Updates replayed while a session is loaded have no active request and are dropped
        ActiveRequest active = activeRequests.get(notification.sessionId());
        if (active != null) {
            active.handle(update);
        }
    }

    private AcpSchema.RequestPermissionResponse handlePermissionRequest(AcpSchema.RequestPermissionRequest request) {
        ActiveRequest active = activeRequests.get(request.sessionId());
        if (active == null || request.options() == null || request.options().isEmpty()) {
            return new AcpSchema.RequestPermissionResponse(new AcpSchema.PermissionCancelled());
        }
        return new AcpSchema.RequestPermissionResponse(active.requestPermission(request.toolCall(), request.options()));
    }

}
