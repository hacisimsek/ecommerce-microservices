package com.hacisimsek.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Adds {@code shared-defaults.yml} as the last (lowest precedence) property source.
 * Unlike {@code spring.config.import}, whose imported values win over the importing file,
 * this keeps every service free to override a shared default in its own configuration.
 */
public class SharedDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String RESOURCE = "shared-defaults.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            new YamlPropertySourceLoader()
                    .load("sharedDefaults", new ClassPathResource(RESOURCE))
                    .forEach(environment.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + RESOURCE, e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
