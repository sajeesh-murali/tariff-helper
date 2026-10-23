package com.tariffhelper.llm;

/**
 * The LLM could not give a usable answer (network problem, API error, or output that failed validation).
 * Messages must stay generic: never put request data or model output in them.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }
}
