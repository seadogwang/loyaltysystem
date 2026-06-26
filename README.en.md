# Loyalty Platform

## Overview

Loyalty Platform is an **omni-channel loyalty management platform** built for enterprises operating across multiple retail channels (Tmall, JD.com, Douyin, WeChat Mini Programs).

## Key Features

- **Multi-Tenant Isolation** — 4-layer defense system
- **One-ID Enrollment** — Cross-channel member matching & deduplication
- **Points Accounting** — FIFO waterfall redemption, negative balance risk control
- **Drools Rule Engine** — DRL rules with hot-reload, shadow sandbox regression
- **AI Rule Assistant (V4)** — Conversational AI with streaming SSE output, clarification forms, dynamic formSchema generation, multi-scenario coverage (base rules, promos, tiered, cyclic)
- **LiteFlow Pipeline** — 7-component event processing chain with visual designer
- **Schema-Driven UI** — Dynamic entity model with REST API auto-generation
- **LLM Configuration** — Multi-provider support (DeepSeek, Bailian, Claude) with masked key management
- **Campaign Tools** — Full-stack marketing planning & execution platform (v7.4)

## Campaign Tools (v7.4)

Campaign Tools is a 12-module AI-driven marketing decision & execution platform integrated with the Loyalty core.

```
Campaign Workflow:
  Planning Workspace → Opportunity Intelligence → Decision Engine
       → Simulation & Optimization → Canvas Editor
       → Compiler (DAG→BPMN) → Execution Engine (Zeebe)
       → Event System → Feedback Loop → (loop back to Planning)
```

### Modules

| Module | Description | Key Endpoint |
|--------|-------------|-------------|
| Planning Workspace | Workspace/Goal/Initiative/Portfolio CRUD + greedy optimization | `POST /api/campaign/workspace` |
| Opportunity Intelligence | ML scoring + RFM + external signal weighted opportunity discovery | `POST /api/campaign/opportunity/discover` |
| Decision Engine | Budget allocation, attention budget, conflict arbitration, prioritization | `POST /api/campaign/decision/execute` |
| Simulation & Optimization | 3-layer prediction, What-if comparison, genetic algorithm optimization | `POST /api/campaign/simulation/run` |
| Execution Engine (Zeebe) | BPMN deploy/start, 8 worker types, pause/resume/cancel | `POST /api/campaign/execution/{planId}/start` |
| Event System + Feedback Loop | 13 event types, feedback metrics, model drift detection, strategy adjustment | `GET /api/campaign/feedback/{planId}` |
| Canvas → BPMN Compiler | DAG JSON → Zeebe BPMN XML, semantic validation, AI DAG generation | `POST /api/campaign/canvas/compile` |
| Node Config Schema | 12 node types with JSON Schemas, pluggable NodeHandler interface | `GET /api/campaign/nodes/definitions` |
| Content & Compliance | Asset versioning, approval workflow, variable rendering, audit trail | `POST /api/campaign/content/asset` |
| Human Intervention | Pause/resume/cancel, node skip, emergency throttle, kill switch | `POST /api/campaign/intervention/pause` |
| End-to-End Runtime | Execution master/step/user-detail tracking with 10-state machine | `GET /api/campaign/execution/status/{planId}` |
| System Blueprint | 35+ DB tables, 13 Flyway migrations, AI prompt template management | — |

### Frontend Pages (11 pages under /campaign/*)

- `/campaign/workspaces` — Workspace list
- `/campaign/workspace/:id` — Workspace detail (Goals/Initiatives/Portfolios)
- `/campaign/opportunity` — Opportunity intelligence + external signals
- `/campaign/decision` — Decision engine (budget allocation, simulation, attention)
- `/campaign/simulation` — Simulation & optimization dashboard
- `/campaign/canvas/new/:planId` — React Flow canvas editor
- `/campaign/execution` — Execution monitor (deploy, start, pause, resume)
- `/campaign/content` — Content asset management + approval workflow
- `/campaign/intervention` — Intervention dashboard (pause, cancel, throttle)
- `/campaign/feedback` — Feedback analysis (predicted vs actual, drift, adjustments)

### Database Migrations

| Version | Module | Tables |
|---------|--------|--------|
| V1_15 | Planning + Core | workspace, goal, initiative, portfolio, plan, content, intervention, dedup |
| V1_16 | Data Warehouse | member_dim, order_fact, behavior_fact, points_summary, tier_change |
| V1_17 | Opportunity | opportunity, external_signal enhancements |
| V1_18 | Decision Engine | decision_result, budget_allocation, attention_consumption, arbitration_log |
| V1_19 | Simulation | simulation_result, simulation_scenario, optimization_result |
| V1_20 | Execution Engine | zeebe_instance, zeebe_task, dedup enhancement |
| V1_21 | Event System | feedback_metrics, model_drift, strategy_adjustment |
| V1_22 | System Blueprint | prompt_template |
| V1_23 | Compiler | compile_log, plan extension |
| V1_24 | Node System | node_definition (12 seed rows), node_execution_history |
| V1_25 | Content | content_asset_history, variable_binding |
| V1_26 | Intervention | intervention_approval, global_control |
| V1_27 | Runtime | execution_master, execution_step, execution_user_detail |

## AI Rule Assistant

The AI Rule Assistant provides a conversational interface for creating loyalty rules.

- **Streaming SSE Output** — Real-time text streaming
- **Clarification Phase** — Structured questions with clickable options
- **Dynamic Form Generation** — LLM-generated formSchema rendered as Ant Design forms
- **Multi-Scenario Coverage** — Base rules, promo rules, tiered rewards, cyclic deduction

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5, Java 17 |
| Rule Engine | Drools 8.44.2 |
| Flow | LiteFlow 2.12.4, Zeebe 8.5 (Campaign) |
| Database | PostgreSQL 15+ (JSONB) |
| Frontend | React 18, TypeScript, Ant Design 5, React Flow 11.x |
| LLM | DeepSeek V4, Qwen-Max (Bailian) |

## Quick Start

```bash
# Backend
psql -U postgres -c "CREATE DATABASE loyalty_dev;"
psql -U postgres -d loyalty_dev -f src/main/resources/db/migration/V1_*.sql
mvn spring-boot:run

# Frontend
cd src/frontend && npm install && npm run dev

# AI Assistant: http://localhost:5173/rules/ai
# Campaign Workspace: http://localhost:5173/campaign/workspaces
```

## License

MIT
