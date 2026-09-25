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
import com.agentclientprotocol.sdk.spec.AcpClientSession;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.wingman.acp.provider.AcpProvider;
import net.mcreator.wingman.data.AssistantService;
import net.mcreator.wingman.data.ChatConversation;
import net.mcreator.wingman.data.ChatMessage;
import net.mcreator.wingman.data.StreamListener;
import net.mcreator.wingman.mcp.McpToolCallMatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Assistant service backed by an external coding agent that speaks the Agent Client Protocol (ACP). The agent is
 * connected lazily using the transport of the given provider, and one ACP session is kept per conversation.
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
	private final Supplier<AcpProvider> providerSupplier;

	// MCreator MCP server given to each session, null if there is none
	@Nullable private final AcpSchema.McpServer mcpServer;
	@Nullable private final McpToolCallMatcher toolCallMatcher;

	private AcpProvider provider;
	private AcpSyncClient client;
	private List<AcpSchema.AuthMethod> authMethods = List.of();

	// Sessions the connected agent knows about (created or loaded since it was connected)
	private final Set<String> knownSessions = ConcurrentHashMap.newKeySet();
	private final Map<String, ActiveRequest> activeRequests = new ConcurrentHashMap<>();
	private final Map<String, Float> contextUsage = new ConcurrentHashMap<>();

	/**
	 * @param providerSupplier Provider of the agent to connect to, queried whenever a connection is made
	 * @param mcpServer        MCreator MCP server the agent connects to in each session, null if there is none
	 * @param toolCallMatcher  Matcher of the results of that server's tools to the tool calls reported by the agent,
	 *                         null if there is no server
	 */
	public AcpChatService(MCreator mcreator, Supplier<AcpProvider> providerSupplier,
			@Nullable AcpSchema.McpServer mcpServer, @Nullable McpToolCallMatcher toolCallMatcher) {
		super(mcreator);
		this.mcreator = mcreator;
		this.providerSupplier = providerSupplier;
		this.mcpServer = mcpServer;
		this.toolCallMatcher = toolCallMatcher;
	}

	private AcpSyncClient client() {
		AcpSyncClient connecting;
		synchronized (this) {
			if (client != null)
				return client;
			provider = providerSupplier.get();
			connecting = AcpClient.sync(provider.transport()).requestTimeout(REQUEST_TIMEOUT).clientCapabilities(
							// Only allow manipulations of the workspace via official MCP, no direct FS or terminal
							new AcpSchema.ClientCapabilities(new AcpSchema.FileSystemCapability(false, false), false))
					.sessionUpdateConsumer(this::handleSessionUpdate)
					.requestPermissionHandler(this::handlePermissionRequest).build();
			// Published before the agent is initialized and without holding the lock while it is, so a cancelled
			// request can drop the connection, which fails the pending initialization instead of waiting for it
			client = connecting;
		}
		try {
			AcpSchema.InitializeResponse response = connecting.initialize();
			authMethods = response.authMethods() != null ? response.authMethods() : List.of();
			return connecting;
		} catch (RuntimeException e) {
			dropConnection();
			throw e;
		}
	}

	@Nullable private synchronized AcpSyncClient currentClient() {
		return client;
	}

	/**
	 * Closes the connection to the agent. The next request connects again, e.g. to the newly selected provider.
	 */
	public synchronized void disconnect() {
		if (client != null) {
			try {
				client.closeGracefully();
			} catch (RuntimeException e) {
				LOG.warn("Failed to close ACP client", e);
			}
			client = null;
			knownSessions.clear();
		}
	}

	/**
	 * Drops the connection to the agent, for use when the connection failed or a call pending on it has to be aborted:
	 * closing the client fails all of its pending calls. Sessions of the dropped connection are reloaded or restarted
	 * with the history relayed by the next request.
	 */
	private void dropConnection() {
		AcpSyncClient dropped;
		synchronized (this) {
			dropped = client;
			client = null;
			knownSessions.clear();
		}
		if (dropped != null) {
			try {
				dropped.close(); // outside the lock, closing waits for the agent process to exit
			} catch (RuntimeException e) {
				LOG.debug("Failed to close dropped ACP client", e);
			}
		}
	}

	private String workspaceDirectory() {
		return mcreator.getWorkspace().getWorkspaceFolder().getAbsolutePath();
	}

	@Override public void stream(ChatConversation conversation, String messageText, StreamListener listener) {
		if (messageText == null || messageText.isBlank()) {
			return;
		}

		// Messages recorded before this request are relayed to the agent if the conversation gets a fresh session
		List<ChatMessage> history = conversation.messages();
		conversation.addMessage(ChatMessage.user(messageText));

		ActiveRequest active = new ActiveRequest(conversation, listener, toolCallMatcher);
		String sessionId = null;
		clearToolCallMatcher();
		try {
			Session session = ensureSession(conversation);
			sessionId = session.id();
			activeRequests.put(sessionId, active);
			// Instructions and the earlier history are sent only with the first prompt of a new session and are not
			// part of the local history
			String promptText = session.fresh() ? SessionPrompts.firstPrompt(history, messageText) : messageText;
			AcpSchema.PromptResponse response = client().prompt(
					new AcpSchema.PromptRequest(sessionId, List.of(new AcpSchema.TextContent(promptText))));
			active.complete(response.stopReason());
		} catch (Throwable e) {
			// Errors (e.g. ServiceConfigurationError, NoClassDefFoundError) are reported too, so the UI never waits forever
			LOG.warn("ACP prompt failed", e);
			if (!(e instanceof AcpClientSession.AcpError)) {
				// Only a JSON-RPC error proves the agent is still alive. Anything else (e.g. the agent process died, the
				// transport closed, a timeout) may have left the connection unusable, so it is dropped and the next
				// request connects again instead of failing with the same dead connection forever
				dropConnection();
			}
			active.fail(e);
		} finally {
			if (sessionId != null) {
				activeRequests.remove(sessionId, active);
			}
			clearToolCallMatcher();
		}
	}

	private void clearToolCallMatcher() {
		if (toolCallMatcher != null) {
			toolCallMatcher.clear();
		}
	}

	/**
	 * @param id    ACP session id of the conversation
	 * @param fresh true if the session was just created and has no history on the agent side
	 */
	private record Session(String id, boolean fresh) {
	}

	// If the agent requires the user to log in first, the user is asked to and the session is opened once more
	private Session ensureSession(ChatConversation conversation) {
		try {
			return openSession(client(), conversation);
		} catch (AcpClientSession.AcpError e) {
			if (!AcpAuthenticator.isAuthRequired(e))
				throw e;

			LOG.info("Agent {} requires the user to log in", provider.name());
			if (!AcpAuthenticator.login(mcreator, provider, authMethods, this::reconnect))
				throw new IllegalStateException(L10N.t("wingman.auth.cancelled"), e);
			return openSession(client(), conversation);
		}
	}

	private AcpSyncClient reconnect() {
		disconnect();
		return client();
	}

	private Session openSession(AcpSyncClient client, ChatConversation conversation) {
		String sessionId = conversation.sessionId();
		if (sessionId != null && knownSessions.contains(sessionId)) {
			return new Session(sessionId, false);
		}

		if (sessionId != null && client.getAgentCapabilities().supportsLoadSession()) {
			try {
				// The history the agent replays while loading is ignored, the local history is already shown
				client.loadSession(
						new AcpSchema.LoadSessionRequest(sessionId, workspaceDirectory(), mcpServers(client)));
				knownSessions.add(sessionId);
				mcreator.getApplication().getAnalytics().trackEvent("wingman_chat_resume", provider.id());
				return new Session(sessionId, false);
			} catch (RuntimeException e) {
				// The session may belong to another agent or be gone, a new one is started with the history relayed
				LOG.warn("Failed to load ACP session {}, starting a new one", sessionId, e);
			}
		}

		sessionId = client.newSession(new AcpSchema.NewSessionRequest(workspaceDirectory(), mcpServers(client)))
				.sessionId();
		knownSessions.add(sessionId);
		conversation.setSessionId(sessionId);
		mcreator.getApplication().getAnalytics().trackEvent("wingman_chat_new", provider.id());
		return new Session(sessionId, true);
	}

	private List<AcpSchema.McpServer> mcpServers(AcpSyncClient client) {
		if (mcpServer == null)
			return List.of();
		if (!client.getAgentCapabilities().supportsMcpHttp()) {
			LOG.warn("Agent {} does not support HTTP MCP servers, it will not have MCreator tools", provider.name());
			return List.of();
		}
		return List.of(mcpServer);
	}

	@Override public void cancel(ChatConversation conversation) {
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
		// The request has no session yet, so it is still connecting to the agent or opening its session, which can
		// hang until the request timeout. Dropping the connection fails the pending call, so the request fails and the
		// UI is released. Closing waits for the agent process to exit, so it is done off the calling (Swing) thread
		new Thread(this::dropConnection, "Wingman ACP drop").start();
	}

	@Override public float getContextUsage(ChatConversation conversation) {
		String sessionId = conversation.sessionId();
		return sessionId == null ? 0f : contextUsage.getOrDefault(sessionId, 0f);
	}

	@Override public void close() {
		disconnect();
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
