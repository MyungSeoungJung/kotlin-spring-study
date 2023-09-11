package com.example.myapp.post
// query-model, view-model
// domain-model(JPA entity)
data class PostResponse(
    val id : Long,
    val title : String,
    val content : String,
    val createdDate: String
)

data class PostCommentCountResponse(
    val id : Long,
    val title : String,
    val createdDate: String,
    val profileId : Long,
    val nickname: String,
    val commentCount : Long
)
// Java
// String str = null;
// kotlin
// val:str
// 기존의 java, String이 nullable
// {"title": "", "content": ""}
// {"title": ""} -> content null
// 필드가 not-nullable

fun PostCreateRequest.validate() = !(this.title.isEmpty() || this.content.isEmpty())

data class PostCreateRequest(val title : String, val content: String)

data class PostModifyRequest(val title : String?, val content: String?)