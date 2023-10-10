package com.example.myapp.post

// query-model, view-model
// domain-model(JPA entity)
data class PostResponse(
    val id : Long,
    val title : String,
    val content: String,
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

data class PostWithFileResponse(
    val id : Long,
    val title : String,
    val content: String,
    val createdDate: String,
    val files : List<PostFileResponse>
)


data class PostFileResponse(
    val id : Long,
    val postId : Long,
    var uuidFileName : String,
    val originalFileName : String,
    val contentType: String,
)


// Java
// String str = null;

// Kotlin
// val str : String = null; // error

// 기존의 java, String이 nullable
// {"title": "", "content": ""}
// {"title": ""} -> content null

// 필드가 not-nullable
data class PostCreateRequest(val title : String, val content: String)

// 포스트 생성 요청 데이트를 검증하는 메서드
fun PostCreateRequest.validate() =
    !(this.title.isEmpty() || this.content.isEmpty())
data class PostModifyRequest(val title : String?, val content: String?)