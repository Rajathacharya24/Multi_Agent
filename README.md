# 🚀 Self-Hosted Inference Gateway (AI Gateway / LLM Proxy)

A production-ready **Spring Boot** service that acts as a centralized AI Gateway in front of multiple Large Language Model (LLM) providers such as **OpenAI**, **Anthropic (Claude)**, **Google Gemini**, and **local models via Ollama**.

Instead of integrating every AI provider directly into your application, clients communicate with a single gateway that intelligently routes requests, performs semantic caching, tracks costs, and automatically handles provider failures.

Inspired by platforms like **LiteLLM**, **Portkey**, and **OpenRouter**, this project focuses on building the infrastructure layer required for scalable AI applications.

---

# ✨ Features

## 🔌 Provider Abstraction Layer

Uses the **Strategy Pattern** to expose a single, unified API regardless of the underlying AI provider.

* Easily switch between OpenAI, Claude, Gemini, Ollama, or future providers.
* Add new providers without changing client code.
* Centralized request and response normalization.

---

## 🧠 Intelligent Routing

The gateway automatically determines which provider should process each request based on configurable routing rules.

Routing can be based on:

* Preferred provider
* Model availability
* Request latency
* Estimated cost
* User preferences
* Failover policies

---

## ⚡ Semantic Caching

Traditional caches only work for identical prompts.

This gateway uses **embedding similarity** to detect semantically equivalent requests and returns previously generated responses when appropriate.

Benefits include:

* Lower API costs
* Faster response times
* Reduced provider load
* Improved user experience

---

## 🛡️ Resilience & Automatic Fallback

Built using **Resilience4j**, the gateway remains operational even when providers experience failures.

Features include:

* Circuit Breakers
* Retry Policies
* Rate Limiting
* Timeout Handling
* Automatic Provider Failover

Example:

```
OpenAI unavailable
        ↓
Fallback → Claude
        ↓
Fallback → Gemini
        ↓
Fallback → Ollama
```

Applications continue working even if one provider becomes unavailable.

---

## 📊 Cost Tracking & Analytics

Every request records usage statistics including:

* Prompt Tokens
* Completion Tokens
* Total Tokens
* Estimated Cost
* Provider Used
* Model Used
* Response Time
* User/API Key

This enables:

* Usage dashboards
* Cost reporting
* Budget monitoring
* Billing support
* Provider comparison

---

## 📡 Streaming Support

Supports normalized **Server-Sent Events (SSE)** streaming across providers.

Clients receive a consistent streaming interface regardless of the underlying provider.

Supported providers include:

* OpenAI
* Anthropic
* Ollama
* Future providers

---

## 🔐 API Key Management

Supports secure provider credentials using Spring configuration.

Example:

* OpenAI API Key
* Anthropic API Key
* Gemini API Key
* Ollama Local Endpoint

Environment variables are recommended for production deployments.

---

## 📈 Observability

Collect metrics for:

* Request Count
* Error Rate
* Cache Hit Ratio
* Average Latency
* Provider Availability
* Token Usage

Designed to integrate with:

* Micrometer
* Prometheus
* Grafana

---

# 🏗️ Architecture

```
                Client Applications
                        │
                        ▼
          ┌──────────────────────────┐
          │      AI Gateway API       │
          └──────────────────────────┘
                        │
            ┌───────────┴───────────┐
            ▼                       ▼
   Semantic Cache             Request Router
            │                       │
            ▼                       ▼
      Cache Hit?              Provider Selection
            │                       │
            ▼                       ▼
      Return Response      Circuit Breaker Layer
                                    │
                     ┌──────────────┼──────────────┐
                     ▼              ▼              ▼
                 OpenAI         Anthropic       Ollama
                     │              │              │
                     └──────────────┼──────────────┘
                                    ▼
                             Response Normalizer
                                    │
                                    ▼
                           Cost & Usage Tracking
                                    │
                                    ▼
                                 Client
```

---

# 🛠️ Tech Stack

| Component     | Technology                        |
| ------------- | --------------------------------- |
| Language      | Java 17                           |
| Framework     | Spring Boot                       |
| Build Tool    | Maven / Gradle                    |
| Cache         | Redis                             |
| Resilience    | Resilience4j                      |
| HTTP Client   | Spring WebClient                  |
| AI Providers  | OpenAI, Anthropic, Gemini, Ollama |
| Configuration | Spring Configuration Properties   |
| Monitoring    | Micrometer (Optional)             |
| Metrics       | Prometheus + Grafana (Optional)   |

---

# 📦 Getting Started

## Prerequisites

* Java 17+
* Maven or Gradle
* Redis
* API keys for the desired AI providers

---

## Clone the Repository

```bash
git clone https://github.com/yourusername/ai-inference-gateway.git

cd ai-inference-gateway
```

---

## Configure Providers

Update `src/main/resources/application.yml`.

```yaml
ai:
  providers:
    openai:
      api-key: ${OPENAI_API_KEY}

    anthropic:
      api-key: ${ANTHROPIC_API_KEY}

    gemini:
      api-key: ${GEMINI_API_KEY}

    ollama:
      url: http://localhost:11434
```

---

## Run Redis

```bash
docker run -p 6379:6379 redis
```

---

## Start the Application

```bash
./mvnw spring-boot:run
```

The gateway will be available at:

```
http://localhost:8080
```

---

# 📂 Project Structure

```
src
├── controller
├── service
├── provider
│   ├── OpenAIProvider
│   ├── AnthropicProvider
│   ├── GeminiProvider
│   └── OllamaProvider
├── router
├── cache
├── resilience
├── metrics
├── config
└── model
```

---

# 🔄 Request Flow

1. Client sends a prompt to the Gateway.
2. Gateway checks the semantic cache.
3. If a similar response exists, it is returned immediately.
4. Otherwise, the router selects the most appropriate provider.
5. The request passes through the resilience layer.
6. The selected provider generates a response.
7. The response is normalized into a common format.
8. Token usage and costs are recorded.
9. The response is stored in the semantic cache for future requests.
10. The final response is returned to the client.

---

# 🚀 Future Enhancements

* JWT Authentication
* Multi-Tenant Support
* Request Queuing
* Distributed Rate Limiting
* Kubernetes Deployment
* Model Health Monitoring
* Dynamic Routing Policies
* Admin Dashboard
* Vector Database Integration
* Request Replay & Auditing
* OpenTelemetry Tracing
* Load Balancing Across Providers

---

# 🎯 Learning Objectives

This project demonstrates practical experience with:

* Distributed Systems Design
* API Gateway Architecture
* Design Patterns (Strategy, Factory)
* Resilience Engineering
* Semantic Caching
* AI Infrastructure
* Performance Optimization
* Observability
* Cost Optimization
* Production-Ready Spring Boot Development

---

# 📄 License

This project is licensed under the MIT License.
