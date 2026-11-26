"""One framework planning turn. The control plane owns inference and external actions."""
import asyncio
import json
import sys
from typing import Literal, TypedDict

from pydantic import BaseModel, ConfigDict, Field


class Step(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    action: Literal["list", "create", "finish"]
    value: str = Field(max_length=2000)


def receive():
    frame = sys.stdin.buffer.readline(100001)
    if len(frame) > 100000 or not frame.endswith(b"\n"):
        raise ValueError("Invalid frame")
    return json.loads(frame)


def send(frame):
    print(json.dumps(frame, separators=(",", ":")), flush=True)


def infer():
    send({"op": "infer"})
    result = receive()
    if set(result) != {"text"} or not isinstance(result["text"], str):
        raise ValueError("Invalid inference response")
    return result["text"]


async def adk(prompt):
    from google.adk.agents import BaseAgent
    from google.adk.events import Event
    from google.adk.runners import InMemoryRunner
    from google.genai import types

    class Planner(BaseAgent):
        async def _run_async_impl(self, ctx):
            yield Event(author=self.name, content=types.Content(
                role="model", parts=[types.Part(text=infer())]))

    runner = InMemoryRunner(agent=Planner(name="planner"), app_name="zincc")
    try:
        session = await runner.session_service.create_session(app_name="zincc", user_id="run")
        message = types.Content(role="user", parts=[types.Part(text=prompt)])
        async for event in runner.run_async(user_id="run", session_id=session.id, new_message=message):
            if event.is_final_response() and event.content:
                return event.content.parts[0].text
        raise ValueError("No final event")
    finally:
        await runner.close()


async def langgraph(prompt):
    from langgraph.graph import END, START, StateGraph

    class State(TypedDict):
        prompt: str
        answer: str

    graph = StateGraph(State)
    graph.add_node("plan", lambda state: {"answer": infer()})
    graph.add_edge(START, "plan")
    graph.add_edge("plan", END)
    state = await graph.compile().ainvoke({"prompt": prompt})
    return state["answer"]


async def pydantic_ai(prompt):
    from pydantic_ai import Agent
    from pydantic_ai.messages import ModelResponse, TextPart
    from pydantic_ai.models.function import FunctionModel

    async def gateway(messages, info):
        return ModelResponse(parts=[TextPart(infer())])

    return (await Agent(FunctionModel(gateway), retries=0).run(prompt)).output


async def main():
    adapters = {"adk": adk, "langgraph": langgraph, "pydantic-ai": pydantic_ai}
    request = receive()
    if set(request) != {"prompt"} or not isinstance(request["prompt"], str):
        raise ValueError("Invalid request")
    answer = await adapters[sys.argv[1]](request["prompt"])
    send({"op": "propose", "step": Step.model_validate_json(answer).model_dump()})


if __name__ == "__main__":
    asyncio.run(main())
