package com.tariffhelper.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Provider switch, bound from {@code app.llm.provider}. */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(@DefaultValue("openrouter") LlmProvider provider) {
}
