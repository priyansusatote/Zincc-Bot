package io.zincc.model;

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

record SpringChatPort(ModelGateway.Route route, ChatModel client) implements ModelGateway.Port {

	@Override
	public ModelGateway.Part call(String prompt, String instructions) {
		var request = instructions == null ? new Prompt(prompt)
				: new Prompt(List.of(new SystemMessage(instructions), new UserMessage(prompt)));
		return part(this.client.call(request));
	}

	@Override
	public Flux<ModelGateway.Part> stream(String prompt) {
		return this.client.stream(new Prompt(prompt)).map(SpringChatPort::part);
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
