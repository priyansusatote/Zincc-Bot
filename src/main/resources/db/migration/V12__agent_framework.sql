ALTER TABLE zincc.agent_version ADD COLUMN framework text NOT NULL DEFAULT 'native'
  CHECK (framework IN ('native','adk','langgraph','pydantic-ai'));
