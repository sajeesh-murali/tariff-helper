package com.tariffhelper.llm;

/** Which model answers the LLM calls. Chosen with {@code app.llm.provider} (any letter case). */
public enum LlmProvider {
    /** Hosted models through OpenRouter, Llama by default. Default provider. */
    OPENROUTER,
    /** Open-source Llama served by Ollama (or any OpenAI-compatible server). */
    LLAMA,
    OPENAI,
    ANTHROPIC,
    /** Placeholder answers, no external calls. */
    STUB
}
