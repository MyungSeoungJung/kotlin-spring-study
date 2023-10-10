package com.example.myapp.configuration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

// auto-configuration: datasource 객체 생성
// spring boot configuration: database-configuration 객체 생성

@Configuration
class DatabaseConfiguration (val dataSource: DataSource) {
    @Bean
    fun databaseConfig() : DatabaseConfig {
        return DatabaseConfig { useNestedTransactions = true }
    }
    @Bean
    fun database(): Database {
        return Database.connect(dataSource)
    }
}