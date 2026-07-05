# Self-Hosted Inference Gateway (AI Gateway/Proxy)

A Spring Boot service that acts as an infrastructure-level abstraction layer sitting in front of multiple AI providers (such as Claude, OpenAI, and local models via Ollama). It handles intelligent routing, fallback mechanisms, semantic caching, and cost tracking, similar to platforms like LiteLLM or Portkey.

This project demonstrates strong systems-design maturity by tackling the infrastructure layer of modern AI applications.

## 🚀 Advanced Features

- **Provider Abstraction Layer:** 
  A unified interface utilizing the Strategy Pattern. Swap or add new AI providers (OpenAI, Anthropic, Gemini, Ollama) seamlessly without changing client-side code.
- **Semantic Caching:** 
  Reduces API costs and latency by caching responses based on embedding similarity rather than exact text matches.
- **Resilience & Automatic Fallbacks:** 
  Implements rate limiting, circuit breakers (via Resilience4j), and automatic fallback routing. If one provider goes down or rate-limits you, the gateway automatically falls back to an alternative.
- **Cost Tracking & Analytics:** 
  Granular monitoring of token usage and cost analytics tracked per user or API key.
- **Streaming Support:** 
  Normalized Server-Sent Events (SSE) streaming responses across all supported LLM providers.

## 🛠️ Tech Stack

- **Framework:** Java / Spring Boot
- **Resilience:** Resilience4j (Circuit Breakers, Rate Limiters)
- **Database/Cache:** Redis (for Semantic Caching & Rate Limiting)
- **AI Providers:** OpenAI, Anthropic, Ollama, etc.
- **Build Tool:** Maven / Gradle

## 📦 Getting Started

### Prerequisites
- Java 17+
- Maven or Gradle
- Redis (for caching features)
- API Keys for your desired providers (e.g., `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`)

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/ai-inference-gateway.git
   cd ai-inference-gateway
   ```

2. **Configure application properties:**
   Update `src/main/resources/application.yml` with your API keys and Redis configuration.
   ```yaml
   ai:
     providers:
       openai:
         api-key: ${OPENAI_API_KEY}
       anthropic:
         api-key: ${ANTHROPIC_API_KEY}
       ollama:
         url: http://localhost:11434
   ```

3. **Build and run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```

## 🧠 Architecture Overview

The system is designed to handle high-throughput, unpredictable AI API workloads. 

1. **Incoming Request** -> Reaches the normalized Gateway API.
2. **Semantic Cache Check** -> Evaluates if a similar prompt has been answered recently. If yes, returns cached response.
3. **Router / Load Balancer** -> Selects the best provider based on user preference, cost constraints, or availability.
4. **Resilience Layer** -> Wraps the outbound call in a circuit breaker.
5. **Execution** -> Strategy pattern invokes the specific provider (OpenAI, Claude, etc.).
6. **Telemetry** -> Logs token usage and updates cost metrics asynchronously.

## 📝 License
This project is licensed under the MIT License.
