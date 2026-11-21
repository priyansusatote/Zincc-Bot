package io.zincc.model;

import java.util.ArrayList;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

record SpringChatPort(ModelGateway.Route route, ChatModel client) implements ModelGateway.Port {

	@Override
	public ModelGateway.Part call(ModelGateway.Input prompt, String instructions) {
		return part(this.client.call(request(prompt, instructions)));
	}

	@Override
	public Flux<ModelGateway.Part> stream(ModelGateway.Input prompt) {
		return this.client.stream(request(prompt, null)).map(SpringChatPort::part);
	}

	private static Prompt request(ModelGateway.Input input, String instructions) {
		var messages = new ArrayList<Message>();
		if (instructions != null)
			messages.add(new SystemMessage(instructions));
		for (var exchange : input.history()) {
			messages.add(new UserMessage(exchange.question()));
			messages.add(new AssistantMessage(exchange.answer()));
		}
		messages.add(new UserMessage(input.question()));
		return new Prompt(messages);
	}

	private static ModelGateway.Part part(ChatResponse response) {
		var usage = response.getMetadata().getUsage();
		var counts = usage == null || usage instanceof EmptyUsage ? null
				: new ModelGateway.Usage(usage.getPromptTokens(), usage.getCompletionTokens());
		var result = response.getResult();
		String text = result == null ? null : result.getOutput().getText();
		String finish = result == null ? null : result.getMetadata().getFinishReason();
		return new ModelGateway.Part(text == null ? "" : text, counts, finish);
	}
}
