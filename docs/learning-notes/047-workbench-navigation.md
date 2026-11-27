# Keeping a chat draft while navigating the workspace

The workbench has two different kinds of state. PostgreSQL owns conversations, permissions and accepted runs. React owns the current destination, open mobile menu and unsent chat draft. The workspace directory tells the client which destinations are available.

The thread stays mounted when a user opens an agent or a document tool. Its draft, selected model and replay cursor survive the trip. Picking another conversation changes the thread key and loads that conversation's snapshot. Tool pages mount only while selected, so a closed runner page releases its polling loop. The agent roster uses the same definitions, permissions and run endpoints as the previous picker.

**Verification.** The Edge navigation test types a draft, visits research, returns to chat and checks the actual input value. It repeats navigation on a phone-sized viewport and checks for horizontal overflow. Existing tests cover approval decisions, engine selection, interrupted message retries, workspace switching and sign-in controls. The desktop and phone images in the workbench guide use synthetic conversations.

**Limit.** A draft lives in the current mounted thread. Reloading the page, switching workspaces or starting another thread can discard it. Cross-session draft persistence would require its own storage and privacy decisions.
