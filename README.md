# RecoverAI — AI Revenue Recovery Agent

RecoverAI is an AI-assisted payment recovery platform designed to identify failed payments, determine appropriate recovery actions, execute controlled retry workflows, and escalate exhausted cases to manual review.

The system provides an operational dashboard for monitoring payment health, recovery attempts, recovery performance, and AI-generated recovery decisions.

## Live Demo

https://recover-ai-frontend-xjn1.onrender.com





## Overview

Payment failures can result in significant revenue leakage if they are not handled appropriately.

RecoverAI models a simplified revenue-recovery workflow:


Payment Failure
      │
      ▼
Recovery Candidate
      │
      ▼
AI Recovery Classification
      │
      ├── Retry Later
      │       │
      │       ▼
      │   Scheduled Retry
      │       │
      │       ├── Success ──────► Recovered
      │       │
      │       └── Failure
      │               │
      │               ▼
      │        Retry Policy Check
      │               │
      │        ┌──────┴──────┐
      │        │             │
      │   Attempts Left   Exhausted
      │        │             │
      │        ▼             ▼
      │   Retry Again    Manual Review
      │
      └── Other / No Action


      Key Features
Payment Management
View all processed payments
Track payment status
View payment amount and currency
Track failure reasons
Track retry counts
Search and filter payment records
Analyze individual payment recovery decisions
AI Recovery Decisions

For failed payments, RecoverAI can generate a recovery decision containing:

Recommended recovery strategy
Diagnosis
Recommended action
Risk level
Policy status
Maximum allowed attempts
Retry delay
Decision confidence
Decision reasoning

Previously generated decisions can also be retrieved instead of unnecessarily regenerating them.

Automated Recovery

The backend supports controlled recovery workflows including:

Recovery attempt creation
Scheduled execution
Retry handling
Successful recovery
Failed retry tracking
Retry-limit enforcement
Manual-review escalation
Recovery Attempt Tracking

Each recovery attempt records information such as:

Payment ID
Attempt number
Recovery strategy
Status
Scheduled execution time
Actual execution time
Result
Creation timestamp

This provides an auditable recovery trail.

Manual Review

Payments that exhaust their automated recovery policy can be escalated to manual review.

This prevents the system from retrying indefinitely and provides a clear boundary between automated recovery and human intervention.

Recovery Simulation

RecoverAI includes a recruiter/demo-oriented simulation environment that can:

Create synthetic payment scenarios
Configure payment amount
Select payment outcome
Select failure reason
Run recovery simulations
Generate multiple payment scenarios
Observe projected recovery outcomes

This makes the project easy to demonstrate without requiring real payment-provider integrations.

Operational Dashboard

The dashboard provides an overview of:

Total payments
Recovery rate
Successfully recovered payments
Failed payments
Manual-review payments
Recovery activity
Payment health
Recent payments
Recovery timeline
Architecture
                     ┌─────────────────────────┐
                     │      React Frontend      │
                     │        Vite + JS         │
                     └────────────┬────────────┘
                                  │
                                  │ REST API
                                  ▼
                     ┌─────────────────────────┐
                     │    Spring Boot Backend  │
                     │                         │
                     │  Controllers            │
                     │  Services               │
                     │  Recovery Engine        │
                     │  AI Classification      │
                     │  Simulation              │
                     └────────────┬────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
          ┌──────────────────┐       ┌──────────────────┐
          │   PostgreSQL     │       │   Gemini API     │
          │                  │       │                  │
          │ Payments         │       │ Recovery         │
          │ Attempts         │       │ Classification   │
          │ Recovery data    │       │                  │
          └──────────────────┘       └──────────────────┘
Tech Stack
Frontend
React
Vite
JavaScript
Axios
Recharts
Lucide React
CSS
Backend
Java 21
Spring Boot
Spring Web
Spring Data JPA
Hibernate
Spring Boot Actuator
Maven
Database
PostgreSQL
AI
Google Gemini API
Infrastructure
Docker
Render
GitHub
Project Structure
recover-ai/
│
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── recoverai/
│   │       │           ├── ai/
│   │       │           ├── config/
│   │       │           ├── controller/
│   │       │           ├── model/
│   │       │           ├── repository/
│   │       │           └── service/
│   │       │
│   │       └── resources/
│   │           └── application.properties
│   │
│   ├── Dockerfile
│   ├── pom.xml
│   └── mvnw.cmd
│
├── frontend/
│   ├── src/
│   │   ├── App.jsx
│   │   ├── App.css
│   │   └── ...
│   │
│   ├── public/
│   ├── package.json
│   └── vite.config.js
│
└── README.md
API

The backend exposes REST endpoints under:

/api
Payments
GET /api/payments

Returns payment records.

Recovery Attempts
GET /api/recovery-attempts

Returns recovery attempt history.

Execute Recovery Attempt
POST /api/recovery-attempts/{id}/execute

Executes a recovery attempt.

Payment Recovery Decision

For an existing decision:

GET /api/payments/{id}/decision

Generate/classify a recovery decision:

POST /api/payments/{id}/classify
Health
GET /actuator/health

The deployed backend exposes Spring Boot health information and currently reports:

{
  "status": "UP"
}
Example Recovery Flow

Consider a failed payment:

Payment
────────────────────────
Customer: recruiter_demo
Amount: ₹12,999
Status: FAILED
Failure: INSUFFICIENT_FUNDS
Retry Count: 0

RecoverAI evaluates the payment and determines an appropriate recovery strategy.

Example:

Recommended Strategy
RETRY_LATER

Diagnosis
Insufficient funds caused the payment failure.

Recommended Action
Schedule a later retry.

Risk Level
LOW

Policy Status
ELIGIBLE

Max Attempts
3

Retry Delay
60s

Decision Confidence
92%

The recovery attempt is then persisted and executed according to the configured policy.

If the retry succeeds:

Payment
FAILED → SUCCESS

If retries continue failing until the policy limit is reached:

Payment
FAILED → MANUAL_REVIEW
Recovery Policy

RecoverAI uses bounded recovery behavior rather than retrying indefinitely.

A simplified recovery lifecycle is:

FAILED
  │
  ▼
Attempt 1
  │
  ├── SUCCESS ──────► SUCCESS
  │
  └── FAILED
        │
        ▼
     Attempt 2
        │
        ├── SUCCESS ──► SUCCESS
        │
        └── FAILED
              │
              ▼
           Attempt 3
              │
              ├── SUCCESS ──► SUCCESS
              │
              └── FAILED
                    │
                    ▼
              MANUAL_REVIEW

This makes recovery behavior predictable and auditable.

Simulation

The application includes a controlled simulation interface for demonstrating recovery behavior.

A synthetic payment can be configured using:

Customer ID
Amount
Payment outcome
Failure reason

Example:

Customer ID: recruiter_demo
Amount: ₹12,999
Payment Outcome: Failed
Failure Reason: Insufficient Funds

The simulation can then be used to demonstrate:

Payment creation
Failure classification
Recovery decision
Retry scheduling
Recovery execution
Successful recovery or retry failure
Manual-review escalation
Local Development
Prerequisites

Install:

Java 21
Maven (or use the included Maven wrapper)
Node.js
PostgreSQL
Git


Backend

Navigate to the backend:

cd backend

Set the required environment variables.

Example:

$env:DATABASE_URL="jdbc:postgresql://localhost:5432/recoverai"
$env:DATABASE_USERNAME="recoverai"
$env:DATABASE_PASSWORD="recoverai"
$env:GEMINI_API_KEY="your-api-key"

Run the application:

.\mvnw.cmd spring-boot:run

The backend runs on:

http://localhost:8080

Health check:

http://localhost:8080/actuator/health
Frontend

Navigate to the frontend:

cd frontend

Install dependencies:

npm install

Create a local environment file:

VITE_API_URL=http://localhost:8080/api

Start the development server:

npm run dev

The frontend will normally be available at:

http://localhost:5173
Environment Variables
Backend
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
GEMINI_API_KEY
GEMINI_MODEL
FRONTEND_URL
PORT
RECOVER_AI_SIMULATOR_FORCE_FAILURE

Example:

DATABASE_URL=jdbc:postgresql://localhost:5432/recoverai
DATABASE_USERNAME=recoverai
DATABASE_PASSWORD=recoverai
GEMINI_API_KEY=your-api-key
GEMINI_MODEL=gemini-3.6-flash
FRONTEND_URL=http://localhost:5173
PORT=8080
RECOVER_AI_SIMULATOR_FORCE_FAILURE=false
Frontend
VITE_API_URL

Example:

VITE_API_URL=http://localhost:8080/api

For the deployed frontend:

VITE_API_URL=https://recover-ai-db7u.onrender.com/api

Environment files containing secrets should not be committed to Git.

Docker

The backend can be packaged as a Docker image.

From the backend directory:

docker build -t recoverai-backend .

Run the container:

docker run -p 8080:8080 recoverai-backend

Environment variables can be supplied through Docker:

docker run `
  -p 8080:8080 `
  -e DATABASE_URL="your-database-url" `
  -e DATABASE_USERNAME="your-username" `
  -e DATABASE_PASSWORD="your-password" `
  -e GEMINI_API_KEY="your-api-key" `
  recoverai-backend
Deployment

The current deployment uses:

GitHub
   │
   ├──► Render Web Service
   │       │
   │       └── Spring Boot Backend
   │
   └──► Render Static Site
           │
           └── React/Vite Frontend

The backend is deployed as a Spring Boot service and connects to PostgreSQL.

The frontend is deployed separately and communicates with the backend through REST APIs.

CORS is configured to allow the deployed frontend as well as local development origins.

Production Verification

The deployed backend exposes:

GET /
GET /actuator/health
GET /api/payments
GET /api/recovery-attempts

Example:

https://recover-ai-db7u.onrender.com/

returns:

{
  "service": "RecoverAI Backend",
  "health": "/actuator/health",
  "api": "/api",
  "status": "UP"
}
Example Recovery Data

A successful recovery attempt looks like:

{
  "attemptNumber": 1,
  "paymentId": 4,
  "strategy": "RETRY_LATER",
  "status": "SUCCESS",
  "result": "Payment recovered successfully"
}

A failed recovery attempt is persisted separately:

{
  "attemptNumber": 3,
  "paymentId": 2,
  "strategy": "RETRY_LATER",
  "status": "FAILED",
  "result": "Payment retry failed"
}

After the configured retry limit is reached, the payment can transition to:

MANUAL_REVIEW
Design Considerations
Bounded Automation

Recovery actions are policy-bounded. The system does not retry payments indefinitely.

Auditability

Recovery attempts are persisted with timestamps, attempt numbers, strategies, statuses, and results.

Separation of Concerns

The system separates:

Payment management
AI classification
Recovery orchestration
Retry execution
Simulation
Persistence
Frontend presentation
AI as a Decision Component

The AI component provides recovery classification and reasoning, while the application retains control over the execution policy.

This prevents the AI layer from directly controlling unrestricted payment operations.

Failure Handling

The application explicitly handles:

Failed payments
Failed recovery attempts
Retry exhaustion
Manual-review escalation
Already recovered payments
Backend/API failures
Future Improvements

Potential production-oriented improvements include:

Authentication and role-based access control
Webhook-based payment ingestion
Real payment-provider integration
Idempotency keys for recovery operations
Distributed job queues
Redis-backed scheduling
Exponential backoff
Recovery strategy experimentation
Event-driven architecture
Observability with metrics and tracing
Rate limiting
Structured logging
Notification workflows
More sophisticated recovery policies
A/B testing of recovery strategies
Historical recovery analytics
Test coverage for recovery state transitions
Why RecoverAI?

RecoverAI demonstrates more than a conventional CRUD application.

The project combines:

REST API development
Spring Boot
Java
PostgreSQL
JPA/Hibernate
AI integration
Business-rule enforcement
Automated workflows
Retry orchestration
State transitions
Failure handling
Audit trails
Data visualization
Simulation
Docker
Cloud deployment
Frontend/backend integration

The core engineering problem is modeling a bounded, auditable automated recovery workflow, rather than simply storing payment records.

Status

Current status: Deployed and functional.

The deployed application currently supports:

Payment management
Recovery attempt tracking
AI recovery classification
Automated recovery execution
Manual-review escalation
Recovery simulation
Operational dashboard
PostgreSQL persistence
REST API
Health monitoring
Dockerized backend
Cloud deployment
