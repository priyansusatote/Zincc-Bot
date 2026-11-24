package io.zincc.agents;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("agents")
@EnableScheduling
class AgentRuntime {

	private static final Logger LOG = LoggerFactory.getLogger(AgentRuntime.class);
	static final String QUEUE = "zincc-agents-v1";

	private final WorkflowServiceStubs service;

	private final WorkflowClient client;

	private final WorkerFactory factory;

	private final AgentStore store;

	AgentRuntime(AgentStore store, AgentEngine engine,
			@Value("${zincc.temporal.target:127.0.0.1:17233}") String target) {
		this.store = store;
		if (!target.matches("127\\.0\\.0\\.1:[0-9]{1,5}"))
			throw new IllegalArgumentException("P3 requires a loopback Temporal endpoint");
		service = WorkflowServiceStubs
			.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
		client = WorkflowClient.newInstance(service);
		factory = WorkerFactory.newInstance(client);
		var worker = factory.newWorker(QUEUE);
		worker.registerWorkflowImplementationTypes(AgentWorkflow.Durable.class);
		worker.registerActivitiesImplementations(engine);
		factory.start();
	}

	static String workflowId(UUID id) {
		return "zincc-agent-" + id;
	}

	@Scheduled(fixedDelay = 60000)
	void purgeExpiredMemory() {
		store.memory.purgeExpired();
	}

	@Scheduled(fixedDelay = 1000)
	void dispatch() {
		for (var work : store.pending()) {
			var options = WorkflowOptions.newBuilder()
				.setTaskQueue(QUEUE)
				.setWorkflowId(workflowId(work.run()))
				.setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
				.build();
			var workflow = client.newWorkflowStub(AgentWorkflow.class, options);
			try {
				WorkflowClient.start(workflow::execute, work.workspace().toString(), work.actor().toString(),
						work.run().toString());
			}
			catch (WorkflowExecutionAlreadyStarted duplicate) {
				/* Previous delivery reached Temporal. */}
			catch (io.temporal.client.WorkflowServiceException ex) {
				LOG.warn("Temporal dispatch unavailable; outbox retained");
				return;
			}
			store.dispatched(work);
		}
	}

	void wake(UUID run) {
		try {
			client.newWorkflowStub(AgentWorkflow.class, workflowId(run)).changed();
		}
		catch (io.temporal.client.WorkflowException ex) {
			LOG.debug("Run decision persisted; durable workflow will recheck");
		}
	}

	@PreDestroy
	void close() {
		factory.shutdown();
		factory.awaitTermination(5, TimeUnit.SECONDS);
		service.shutdown();
	}

}
