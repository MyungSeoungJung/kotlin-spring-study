package com.example.myapp.post

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.annotation.Configuration


object Posts  : Table("post") {
        val id = long("id").autoIncrement()
    val title = varchar("title", 100)
    val content = text("content")
    val createdDate  = datetime("created_date")
        override val primaryKey = PrimaryKey(id, name = "pk_post_id")

}
//id가 long
object PostComments :LongIdTable(name = "post_comment"){


    val postId = reference(name = "post_id", Posts.id)
    val comment = text("comment")
}

@Configuration
class PostTableSetup(private val database : Database){

    @PostConstruct
    fun migrateSchema(){
    transaction {
        SchemaUtils.createMissingTablesAndColumns(Posts,PostComments)
    }
    }
}