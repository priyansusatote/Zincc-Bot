package io.zincc.agents;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

@WorkflowInterface
public interface AgentWorkflow {

	@WorkflowMethod
	void execute(String workspace, String actor, String run);

	@SignalMethod
	void changed();

	@ActivityInterface
	interface Steps {

		@ActivityMethod
		String advance(String workspace, String actor, String run, boolean expired);

	}

	class Durable implements AgentWorkflow {

		private int changes;

		private final Steps steps = Workflow.newActivityStub(Steps.class,
				ActivityOptions.newBuilder()
					.setStartToCloseTimeout(Duration.ofSeconds(100))
					.setRetryOptions(RetryOptions.newBuilder()
						.setInitialInterval(Duration.ofSeconds(2))
						.setMaximumInterval(Duration.ofMinutes(1))
						.build())
					.build());

		public void changed() {
			changes++;
		}

		public void execute(String workspace, String actor, String run) {
			long deadline = Workflow.currentTimeMillis() + Duration.ofHours(24).toMillis();
			while (true) {
				int before = changes;
				String state = steps.advance(workspace, actor, run, Workflow.currentTimeMillis() >= deadline);
				if (state.equals("waiting"))
					Workflow.await(Duration.ofMinutes(2), () -> changes != before);
				else if (!state.equals("running") && !state.equals("approved"))
					return;
			}
		}

	}

}
