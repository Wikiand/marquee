package com.marquee.web;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

public final class TemplateRenderer {

    private final TemplateEngine engine;

    public TemplateRenderer() {
        ClassLoaderTemplateResolver resolver =
            new ClassLoaderTemplateResolver();

        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    public String render(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return engine.process(template, context);
    }
}
