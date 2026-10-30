package com.xperp.ai.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Prompts versionados en {@code resources/prompts/<nombre>.v<N>.st} (AGENTS.md, 8.8). Se usa
 * siempre la versión más alta; la versión queda en {@code ai_decisions.prompt_version}.
 */
@Component
public class PromptLibrary {

  public record Prompt(String name, String version, String template) {}

  private final Map<String, Prompt> prompts = new ConcurrentHashMap<>();

  PromptLibrary() throws IOException {
    Resource[] resources =
        new PathMatchingResourcePatternResolver().getResources("classpath:prompts/*.st");
    for (Resource r : resources) {
      String file = r.getFilename();
      if (file == null) {
        continue;
      }
      String base = file.substring(0, file.length() - 3);
      int dot = base.lastIndexOf(".v");
      String name = dot > 0 ? base.substring(0, dot) : base;
      String version = dot > 0 ? base.substring(dot + 1) : "v1";
      String template = r.getContentAsString(StandardCharsets.UTF_8);
      Prompt candidate = new Prompt(name, name + "." + version, template);
      prompts.merge(name, candidate, (a, b) -> a.version().compareTo(b.version()) >= 0 ? a : b);
    }
  }

  public Prompt get(String name) {
    Prompt p = prompts.get(name);
    if (p == null) {
      throw new IllegalArgumentException("No existe el prompt " + name);
    }
    return p;
  }

  public String version(String name) {
    Prompt p = prompts.get(name);
    return p == null ? name : p.version();
  }
}
