package io.zincc.agents;

import java.util.List;

/** Version-one tool manifests for the task-list connector. */
enum TaskTool {

	LIST("list", "Read up to twelve titles from the connected task list", false),
	CREATE("create", "Create one task with the exact approved title", true);

	final String action;

	final String description;

	final boolean approval;

	TaskTool(String action, String description, boolean approval) {
		this.action = action;
		this.description = description;
		this.approval = approval;
	}

	boolean granted(AgentStore.Agent agent) {
		return approval ? agent.canWrite() : agent.canRead();
	}

	static String describe(AgentStore.Access access) {
		return List.of(values())
			.stream()
			.filter(t -> t.granted(access.version()) && t.granted(access.latest()))
			.map(t -> t.action + " = " + t.description)
			.collect(java.util.stream.Collectors.joining("; "));
	}

}
