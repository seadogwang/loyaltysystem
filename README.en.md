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

## AI Rule Assistant

The AI Rule Assistant provides a conversational interface for creating loyalty rules. Key capabilities:

- **Streaming SSE Output** — Real-time text streaming with `flushSync` rendering
- **Clarification Phase** — Structured questions with clickable options (V3)
- **Dynamic Form Generation** — LLM-generated `formSchema` rendered as Ant Design forms
- **Multi-Scenario Coverage** — Base rules, promo rules, tiered rewards, cyclic deduction
- **Intent Recognition** — Auto-identifies rule type, reward mode, product scope

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/rules/ai/start` | Start AI session |
| `POST` | `/api/rules/ai/clarify` | Streaming SSE clarification |
| `POST` | `/api/rules/ai/clarify/submit` | Submit clarification answers |
| `POST` | `/api/rules/ai/submit-form` | Submit form, generate rule |
| `POST` | `/api/rules/ai/save` | Save rule (draft/publish) |
| `GET` | `/api/admin/llm-config` | Get LLM configuration |
| `PUT` | `/api/admin/llm-config` | Save LLM configuration |
| `POST` | `/api/admin/llm-config/test` | Test LLM connection |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5, Java 17 |
| Rule Engine | Drools 8.44.2 |
| Flow | LiteFlow 2.12.4 |
| Database | PostgreSQL 15+ |
| Frontend | React 18, TypeScript, Ant Design 5 |
| LLM | DeepSeek V4, Qwen-Max (Bailian) |

## Quick Start

```bash
# Backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd src/frontend && npm install && npm run dev

# AI Assistant: http://localhost:5173/rules/ai
# LLM Config: http://localhost:5173/system/llm-config
```