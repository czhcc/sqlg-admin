package com.trs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台业务配置,绑定 application.yml 中 sqlg-admin 前缀的配置项。
 *
 * @author czh
 * @date 2026/07/10
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sqlg-admin")
public class PlatformConfig {

    private GremlinConfig gremlin = new GremlinConfig();
    private GraphExpandConfig graphExpand = new GraphExpandConfig();
    private ImportConfig importConfig = new ImportConfig();

    @Data
    public static class GremlinConfig {
        private int maxResultSize = 1000;
        private int timeoutSeconds = 30;
        private boolean readonlyMode = false;
        private List<String> blockedKeywords = new ArrayList<>();
    }

    @Data
    public static class GraphExpandConfig {
        private int maxDepth = 3;
        private int maxNodes = 500;
        private int maxEdges = 1000;
    }

    @Data
    public static class ImportConfig {
        private int batchSize = 1000;
    }
}
