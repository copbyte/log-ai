package com.logmonitor.log.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * H2 嵌入式 DataSource 配置（独立于主 MySQL DataSource）
 * <p>
 * 用于存储日志文件读取偏移量（file_offset 表），
 * 进程崩溃或重启后可从 H2 恢复，避免重复采集。
 */
@Configuration
public class OffsetDataSourceConfig {

    @Bean(name = "offsetDataSourceProperties")
    public DataSourceProperties offsetDataSourceProperties() {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl("jdbc:h2:file:./data/log-offset;MODE=MySQL;AUTO_SERVER=TRUE");
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        return props;
    }

    @Bean(name = "offsetDataSource")
    public DataSource offsetDataSource(
            @Qualifier("offsetDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "offsetJdbcTemplate")
    public JdbcTemplate offsetJdbcTemplate(
            @Qualifier("offsetDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
