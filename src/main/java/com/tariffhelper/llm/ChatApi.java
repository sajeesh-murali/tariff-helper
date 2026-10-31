package com.tariffhelper.llm;

/** One chat call to a model provider (Anthropic, OpenAI or Llama). Shared by every LLM-backed client. */
public interface ChatApi {

    String model();

    /** Token limit configured for this provider. Each call may pass its own limit to {@link #send}. */
    int maxTokens();

    /** Sends one user message and returns the text of the answer (may be empty). Throws LlmException on failure. */
    String send(String system, String user, int maxTokens);
}
