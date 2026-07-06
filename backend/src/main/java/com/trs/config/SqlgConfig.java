package com.trs.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.Properties;

@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "sqlg.enabled", havingValue = "true", matchIfMissing = false)
public class SqlgConfig {

    private static final Logger log = LoggerFactory.getLogger(SqlgConfig.class);

    @Value("${sqlg.properties-file:sqlg.properties}")
    private String propertiesFile;

    private SqlgGraph sqlgGraph;

    @PostConstruct
    public void init() {
        try {
            Properties props = new Properties();
            try (var in = new ClassPathResource(propertiesFile).getInputStream()) {
                props.load(in);
            }
            var config = new BaseConfiguration();
            props.forEach((k, v) -> config.setProperty((String) k, v));
            log.info("Opening SqlgGraph with jdbc.url={}", props.getProperty("jdbc.url"));
            this.sqlgGraph = SqlgGraph.open(config);
            log.info("SqlgGraph opened successfully");
        } catch (Exception e) {
            log.warn("SqlgGraph not initialized (will retry lazily): {}", e.getMessage());
        }
    }

    @Bean
    public Graph graph() {
        return sqlgGraph;
    }

    @PreDestroy
    public void close() {
        if (sqlgGraph != null) {
            try {
                sqlgGraph.close();
                log.info("SqlgGraph closed");
            } catch (Exception e) {
                log.warn("Failed to close SqlgGraph: {}", e.getMessage());
            }
        }
    }
}

