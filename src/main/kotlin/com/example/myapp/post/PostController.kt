package com.example.myapp.post

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PostResponse(
    val id : Long,
    val title : String,
    val content: String,
    val createdDate: String
)

class PostController {

    @RestController
    @RequestMapping("posts")
    class PostController{



        @GetMapping
        fun fetch() = transaction {
            Posts.selectAll().map { r -> PostResponse(
                r[Posts.id], r[Posts.title],r[Posts.content],r[Posts.createdDate].toString()
            )}
        }
    }
}