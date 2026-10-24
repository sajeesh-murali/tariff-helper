package com.tariffhelper.llm;

/** Which model answers the LLM calls. Chosen with {@code app.llm.provider} (any letter case). */
public enum LlmProvider {
    /** Open-source Llama served by Ollama (or any OpenAI-compatible server). Default. */
    LLAMA,
    OPENAI,
    ANTHROPIC,
    /** Placeholder answers, no external calls. */
    STUB
}
