# On-device Ticket Agent

The **Chat with AI Agent** screen is backed by an on-device agent that can read and modify the
user's Mesha tickets while it answers. It runs entirely through the local Gemma / LiteRT-LM
model — no cloud AI provider — reusing the same [`LocalAiProvider`](../../mobile/app/src/main/java/com/mesha/mobile/domain/ai/LocalAiProvider.kt)
inference that powers on-device issue drafting.

## What it can do

Through natural-language chat the agent can:

- **Read** — list tickets in the active project, get one ticket's details, list its comments,
  and list the project's statuses, the workspace's labels, and assignable members.
- **Write** — create a ticket; update a ticket's title, description, status, priority, labels
  and assignee; and add comments.

It only ever operates in the **currently selected workspace/project**
([`SelectionStore`](../../mobile/app/src/main/java/com/mesha/mobile/data/repository/SelectionStore.kt)).
If no project is selected, ticket tools return a clear message asking the user to open one.
All actions go through the existing REST API via
[`MeshaRepository`](../../mobile/app/src/main/java/com/mesha/mobile/data/repository/MeshaRepository.kt)
under the user's own Clerk session — the agent has exactly the permissions the user does, and
(per the platform rule) it never merges or deploys anything.

## Why a prompt-driven loop

The on-device engines (MediaPipe `tasks-genai`, LiteRT-LM) only do plain text generation — no
native function/tool calling and no system role. So the agent is a **ReAct-style loop built on
top of plain text**, mirroring how the issue-draft feature already coaxes structured JSON out of
a small model:

1. [`AgentPromptBuilder`](../../mobile/app/src/main/java/com/mesha/mobile/domain/ai/agent/AgentPromptBuilder.kt)
   renders one prompt per turn in Gemma turn format: a preamble (role, the JSON action protocol,
   the tool catalog, active-project context), the conversation so far, and the current turn's
   scratchpad (each tool call already made this turn plus its `Observation:`).
2. The model replies with a single JSON object — either a tool call
   (`{"tool":"…","arguments":{…}}`) or a final answer (`{"final":"…"}`).
3. [`AgentActionParser`](../../mobile/app/src/main/java/com/mesha/mobile/domain/ai/agent/AgentActionParser.kt)
   extracts that action, tolerating the messy shapes small models emit (markdown fences, prose,
   `action`/`args` aliases, flat argument siblings, plain-prose answers with no JSON).
4. [`TicketAgent`](../../mobile/app/src/main/java/com/mesha/mobile/domain/ai/agent/TicketAgent.kt)
   executes tool calls against the [tools](../../mobile/app/src/main/java/com/mesha/mobile/domain/ai/agent/TicketTools.kt),
   feeds the observation back, and loops until the model answers or a small step budget is hit
   (which bounds latency on weak devices and guarantees termination).

## Robustness for small models

Weak on-device models are unreliable at chaining "look up the id, then act", so the tools accept
**human-friendly references and resolve them internally**:

- a ticket by **identifier or title** (exact, then substring);
- **labels / statuses / assignees by name or email**, resolved to ids against the workspace/
  project (unknown labels are skipped and reported; unknown statuses/assignees are rejected with
  the valid options);
- `assignee: "none"` (and similar) clears the assignee; `labels` replaces the set while
  `add_labels` / `remove_labels` adjust it incrementally.

No tool throws for an expected failure — each returns a short observation the model can reason
about or relay, keeping the loop stable.

## Where the pieces live

```
domain/ai/agent/
├── AgentContext.kt        # active workspace/project passed to tools
├── AgentTool.kt           # tool interface (name, description, argsSpec, execute)
├── AgentAction.kt         # parsed model decision: ToolCall | Final
├── AgentActionParser.kt   # raw model text -> AgentAction (pure, unit-tested)
├── AgentPromptBuilder.kt  # tool catalog + protocol + scratchpad -> prompt (pure, unit-tested)
├── AgentStep.kt           # progress events streamed to the chat UI
├── TicketTools.kt         # the concrete ticket tools, backed by MeshaRepository
├── AgentToolRegistry.kt   # the tool catalog + name lookup
└── TicketAgent.kt         # the think -> act -> observe loop
```

The chat surface —
[`LocalLlmChatViewModel`](../../mobile/app/src/main/java/com/mesha/mobile/ui/screens/chat/LocalLlmChatViewModel.kt)
and `LocalLlmChatScreen` — drives `TicketAgent` and renders its tool activity inline (a small
"🔧 tool · ref" row per step) above the final reply.

## Testing

Pure, JVM-only unit tests run without a device (`./gradlew :app:testDebugUnitTest`):
`AgentActionParserTest`, `AgentPromptBuilderTest`, `TicketAgentTest` (loop behavior with a
scripted model), `TicketToolsTest` (reference resolution against a mocked repository), and the
updated `LocalLlmChatViewModelTest`.
