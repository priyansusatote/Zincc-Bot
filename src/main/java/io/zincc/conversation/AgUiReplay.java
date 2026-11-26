package io.zincc.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

final class AgUiReplay {

	private static final int STRIDE = 8;

	private AgUiReplay() {
	}

	static long afterSequence(long cursor) {
		return Math.max(0, cursor / STRIDE - 1);
	}

	static String frames(UUID thread, List<ConversationStore.Event> events, long cursor, JsonMapper json) {
		var sse = new StringBuilder();
		for (var event : events) {
			var frames = new ArrayList<Map<String, Object>>();
			String run = event.turnId().toString();
			switch (event.kind()) {
				case "started" -> {
					frames.add(Map.of("type", "RUN_STARTED", "threadId", thread.toString(), "runId", run));
					frames.add(Map.of("type", "TEXT_MESSAGE_START", "messageId", run, "role", "assistant"));
				}
				case "delta", "completed" -> {
					if (event.body() != null && !event.body().isEmpty())
						frames.add(Map.of("type", "TEXT_MESSAGE_CONTENT", "messageId", run, "delta", event.body()));
					if (event.kind().equals("completed")) {
						frames.add(Map.of("type", "TEXT_MESSAGE_END", "messageId", run));
						frames.add(Map.of("type", "RUN_FINISHED", "threadId", thread.toString(), "runId", run));
					}
				}
				case "interrupted" -> {
					if (event.body() != null && !event.body().isEmpty())
						frames.add(Map.of("type", "TEXT_MESSAGE_CONTENT", "messageId", run, "delta", event.body()));
					frames.add(Map.of("type", "TEXT_MESSAGE_END", "messageId", run));
					frames.add(Map.of("type", "RUN_ERROR", "message", "Generation interrupted", "code",
							event.failure() == null ? "INTERRUPTED" : event.failure()));
				}
				default -> throw new IllegalStateException("Unsupported conversation event");
			}
			for (int i = 0; i < frames.size(); i++) {
				long id = Math.addExact(Math.multiplyExact(event.seq(), STRIDE), i + 1);
				if (id > cursor)
					sse.append("id: ")
						.append(id)
						.append("\ndata: ")
						.append(json.writeValueAsString(frames.get(i)))
						.append("\n\n");
			}
		}
		return sse.toString();
	}

}
