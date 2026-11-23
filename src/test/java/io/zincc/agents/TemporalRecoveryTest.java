package io.zincc.agents;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ZINCC_TEMPORAL_TEST", matches = "true")
class TemporalRecoveryTest {

	@Test
	void secondWorkerReplaysPersistedTimerAndRepeatedStartCannotDuplicateWorkflow() throws Exception {
		var service = WorkflowServiceStubs
			.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget("127.0.0.1:17233").build());
		var client = WorkflowClient.newInstance(service);
		String id = "zincc-test-" + UUID.randomUUID();
		var options = WorkflowOptions.newBuilder()
			.setTaskQueue(id)
			.setWorkflowId(id)
			.setWorkflowExecutionTimeout(Duration.ofMinutes(3))
			.setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
			.build();
		var approved = new AtomicBoolean();
		AgentWorkflow.Steps steps = (w, a, r, expired) -> approved.get() ? "completed" : "waiting";
		var first = worker(client, id, steps);
		var second = WorkerFactory.newInstance(client);
		try {
			var flow = client.newWorkflowStub(AgentWorkflow.class, options);
			WorkflowClient.start(flow::execute, "workspace", "actor", "run");
			boolean timer = false;
			for (int i = 0; i < 100 && !timer; i++) {
				timer = client.fetchHistory(id)
					.getEvents()
					.stream()
					.anyMatch(e -> e.getEventType() == EventType.EVENT_TYPE_TIMER_STARTED);
				if (!timer)
					Thread.sleep(100);
			}
			assertThat(timer).isTrue();
			first.shutdownNow();
			first.awaitTermination(10, TimeUnit.SECONDS);
			var replacement = second.newWorker(id);
			replacement.registerWorkflowImplementationTypes(AgentWorkflow.Durable.class);
			replacement.registerActivitiesImplementations(steps);
			second.start();
			approved.set(true);
			flow.changed();
			WorkflowStub.fromTyped(flow).getResult(20, TimeUnit.SECONDS, Void.class);
			var again = client.newWorkflowStub(AgentWorkflow.class, options);
			assertThatThrownBy(() -> WorkflowClient.start(again::execute, "workspace", "actor", "run"))
				.isInstanceOf(WorkflowExecutionAlreadyStarted.class);
		}
		finally {
			first.shutdownNow();
			second.shutdownNow();
			second.awaitTermination(10, TimeUnit.SECONDS);
			service.shutdown();
		}
	}

	private WorkerFactory worker(WorkflowClient client, String queue, AgentWorkflow.Steps steps) {
		var factory = WorkerFactory.newInstance(client);
		var worker = factory.newWorker(queue);
		worker.registerWorkflowImplementationTypes(AgentWorkflow.Durable.class);
		worker.registerActivitiesImplementations(steps);
		factory.start();
		return factory;
	}

}
