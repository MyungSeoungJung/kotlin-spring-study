package com.example.myapp.auth

import com.example.myapp.post.PostComments
import com.example.myapp.post.Posts
import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.annotation.Configuration

object Identities : LongIdTable("identity") {
    val secret = varchar("secret", 200)
    val username = varchar("username", length = 100)
}

object Profiles : LongIdTable("profile") {
    val email = varchar("email", 200)
    val nickname = varchar("nickname", 100)
    val identityId = reference("identity_id", Identities )

}

@Configuration
class AuthTableSetup(private val database: Database) {
    @PostConstruct
    fun migrateSchema() {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Identities, Profiles)
        }
    }
}

