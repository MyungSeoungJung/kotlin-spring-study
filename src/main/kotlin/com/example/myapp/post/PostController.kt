package com.example.myapp.post

import com.example.myapp.auth.Auth
import com.example.myapp.auth.AuthProfile
import com.example.myapp.auth.Profiles
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.core.io.ResourceLoader
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("posts")
class PostController(private val resourceLoader: ResourceLoader) {
    private val POST_FILE_PATH = "files/post";

    // exposed selectAll -> List<ResultRow>
    // ResultRow는 transaction {} 구문 밖에서 접근 불가능함
    // transaction 구분 외부로 보낼 때는 별도의 객체로 변환해서 내보낸다.
    // 결과값: List<PostResponse>
    @GetMapping
    fun fetchAllPosts() = transaction {
        val postResponses = Posts.selectAll().map { r ->
            val postId = r[Posts.id]
            val postFiles = PostFiles.select { PostFiles.postId eq postId }.map { fileRow ->
                PostFileResponse(
                    id = fileRow[PostFiles.id].value,
                    postId = postId,
                    uuidFileName = fileRow[PostFiles.uuidFileName],
                    originalFileName = fileRow[PostFiles.originalFileName],
                    contentType = fileRow[PostFiles.contentType]
                )
            }

            PostWithFileResponse(
                id = postId,
                title = r[Posts.title],
                content = r[Posts.content],
                createdDate = r[Posts.createdDate].toString(),
                files = postFiles
            )
        }

        return@transaction ResponseEntity.ok(postResponses)
    }

    @GetMapping("/paging")
    fun paging(@RequestParam size: Int, @RequestParam page : Int)
            : Page<PostResponse> = transaction(
        Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true
    ) {
        // READ_COMMMITED, REPEATABLE_READ
        // 전체조회중 50건정도
        // 누군가 50건 중에 수정중이거나, 삭제중이거나
        // SELECT 잠시 wait

        // Mission Critical 서비스
        // 금융권, 의료, 제조..., 데이터 정확 잘 맞아야 되는 곳

        // TRANSACTION_READ_UNCOMMITTED
        // insert/update/read 트랜잭션 시장
        // 트랜잭션이 커밋이 안 되어도 조회가 가능
        // insert/update/delete 트랜잭션 상관없이 조회 가능

        // 페이징 조회
        // object의 이름을 짧은걸로 변경
        val p = Posts // table alias

        // 페이징 조회
        val content = Posts
            .selectAll()
            .orderBy(Posts.id to SortOrder.DESC)
            .limit(size, offset = (size * page).toLong())
            .map {
                    r -> PostResponse(
                r[p.id], r[p.title],
                r[p.content], r[p.createdDate].toString()
            )
            }

        // 전체 결과 카운트
        val totalCount = Posts.selectAll().count()

        return@transaction PageImpl(
            content, // List<PostResponse>(컬렉션)
            PageRequest.of(page, size), // Pageable
            totalCount // 전체 건수
        )
    }

    //    /paging/search?size=10&page=0
    //    /paging/search?size=10&page=0&keyword="제목"
    @GetMapping("/paging/search")
    fun searchPaging(@RequestParam size : Int, @RequestParam page : Int, @RequestParam keyword : String?) : Page<PostResponse>
            = transaction(Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true) {
        // 검색 조건 생성
        val query = when {
            keyword != null -> Posts.select {
                (Posts.title like "%${keyword}%") or
                        (Posts.content like "%${keyword}%" ) }
            else -> Posts.selectAll()
        }

        // 전체 결과 카운트
        val totalCount = query.count()

        // 페이징 조회
        val content = query
            .orderBy(Posts.id to SortOrder.DESC)
            .limit(size, offset= (size * page).toLong())
            .map { r ->
                PostResponse(r[Posts.id],
                    r[Posts.title],
                    r[Posts.content], r[Posts.createdDate].toString())
            }

        // Page 객체로 리턴
        PageImpl(content, PageRequest.of(page, size),  totalCount)
    }

    @GetMapping("/commentCount")
    fun fetchCommentCount(@RequestParam size : Int, @RequestParam page : Int,
                          @RequestParam keyword : String?) : Page<PostCommentCountResponse>
            = transaction(Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true) {

//        -- select에는 그룹핑 열이 나와줘야 함
//        -- 그룹핑 열은 제외하고는 집계함수(count, sum, avg, max)
//        select p.id, p.title, p.content, p.created_date,
//        pf.nickname,
//        count(c.id) as commentCount
//        from post p
//        inner join profile pf on p.profile_id = pf.id
//                left join post_comment c on p.id = c.post_id
//                -- post의 id값을 기준으로 그룹핑
//                group by p.id, p.title, p.content, p.created_date, pf.nickname;

        // 단축 이름 변수 사용
        val p = Posts;
        val pf = Profiles;
        val c = PostComments;

        // 집계함수식의 별칭 설정
        val commentCount = PostComments.id.count(); // count(c.id) as commentCount

//        ((Posts innerJoin Profiles) leftJoin PostComments)
//            .slice(Posts.id, Posts.title, Posts.createdDate, Posts.profileId, Profiles.nickname,
//                    PostComments.id.count())
//            .selectAll()
//            .groupBy(Posts.id, Posts.title, Posts.createdDate, Posts.profileId, Profiles.nickname)
//            .orderBy(Posts.id to SortOrder.DESC)
//            .limit(size, offset = (size * page).toLong())

        // 조인 및 특정 컬럼 선택 및 count함수 사용
        val slices = ((p innerJoin pf) leftJoin c)
            .slice(p.id, p.title, p.createdDate, p.profileId, pf.nickname, commentCount);

        // 검색 조건 설정
        val query = when {
            keyword != null -> slices.select((Posts.title like "%${keyword}%") or (Posts.content like "%${keyword}%" ))
            else -> slices.selectAll()
        }

        // 전체 결과 카운트
        val totalCount = query.count();

        // 페이징 조회
        val content = query
            .groupBy(p.id, p.title, p.createdDate, p.profileId, pf.nickname)
            .orderBy(p.id to SortOrder.DESC)
            .limit(size, offset= (size * page).toLong())
            .map {
                    r -> PostCommentCountResponse(
                r[p.id], r[p.title], r[p.createdDate].toString(),
                r[p.profileId].value, r[pf.nickname], r[commentCount])
            }

        // Page 객체로 리턴
        return@transaction PageImpl(content, PageRequest.of(page, size), totalCount);
    }



    // PostCreateRequest
    // title: String, content: String -> 둘 다 null이 불가능
    // null체크를 할 필요 없음
    @Auth
    @PostMapping
    fun create(@RequestBody request : PostCreateRequest,
               @RequestAttribute authProfile: AuthProfile) :
            ResponseEntity<Map<String, Any?>> {
        println("${request.title}, ${request.content}")

        // 자바
        // Map<String, Object>
        // Object: nullable, int/long primitive 타입은 안 됨, Integer, Long

        // 코틀린
        // Map<String, Any?>
        // {"key" to null} -> Map<String, Student?>1q
        // {"key" to student} -> Map<String, Student>
        // {"key" to "str"} -> Map<String, String>
        // {"key" to 0L} -> Map<String, Long>
        // Java: Object, class들의 최상위 클래스

        if(!request.validate()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "title and content fields are required"))
        }
//        // not-nullable이기 때문에 null체크를 안 해도 된다.
//        if(request.title.isEmpty() || request.content.isEmpty()){
//            return ResponseEntity
//                    .status(HttpStatus.BAD_REQUEST)
//                .body(mapOf("error" to "title and content fields are required"))
//        }

        // Pair(first, second)
        // Student(name, age, grade)
        // -> componentN() 메서드로 필드 순서를 지정했을 때만
        // 코틀린의 구조분해는 객체의 필드 순서가 정의되어 있을 때만 사용가능
        val (result, response) = transaction {
            // insert 구문
            // List<ResultRow>?

            // ?: -> 앞의 식의 결과가 null이 아니면 앞의 식을 실행,
            //        null이면 뒤이 것을 실행
            val result = Posts.insert {
                // 매개변수 1개의 lambda 함수의 매개변수를 it으로 단축표기
                // 함수식
                it[title] = request.title
                it[content] = request.content
                it[createdDate] = LocalDateTime.now()
                it[profileId] = authProfile.id
            }.resultedValues
                ?:
                // ex) Pair(결과타입, 결과객체)
                // Pairs(first, second)
                return@transaction Pair(false, null)

            // List<ResultRow> -> ResultRow
            val record = result.first()

            // ResultRow -> PostResponse
            return@transaction Pair(true, PostResponse(
                record[Posts.id],
                record[Posts.title],
                record[Posts.content],
                record[Posts.createdDate].toString(),
            ))
        }

        // 정확히 insert 됐을 때
        if(result) {
            return  ResponseEntity
                .status(HttpStatus.CREATED).body(mapOf("data" to response))
        }

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(mapOf("data" to response, "error" to "conflict"))
    }


    @Auth
    @DeleteMapping("/{id}")
    fun remove(@PathVariable id : Long,
               @RequestAttribute authProfile: AuthProfile) : ResponseEntity<Any> {
        // 해당 id의 레코드 있는지 확인
        // 조회결과를 쓰지 않고 있는지 없는지만 판단

        // exposed
        // Posts.select { Posts.id eq id }.firstOrNull()
        // Posts.select( where = Posts.id eq id ).firstOrNull()
        // 반환값: ResultRow?

        // SQL
        // select * from post where id = :id -> ResultRow?
        // [select * from] post [where] id = :id -> ResultRow?
        // post id = :id
        transaction {
            Posts.select(
                where = (Posts.id eq id) and (Posts.profileId eq authProfile.id )
            ).firstOrNull()
        }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        // delete
        transaction {
            Posts.deleteWhere { Posts.id eq id }
        }

        // 200 OK
        return ResponseEntity.ok().build()
    }

    @Auth
    @PutMapping("/{id}")
    fun modify(@PathVariable id : Long,
               @RequestBody request: PostModifyRequest,
               @RequestAttribute authProfile: AuthProfile): ResponseEntity<Any> {
        // 둘다 널이거나 빈값이면 400 : Bad request
        if(request.title.isNullOrEmpty() && request.content.isNullOrEmpty()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "title or content are required"))
        }

        // id에 해당 레코드가 없으면 404
        transaction {
            Posts.select{
                (Posts.id eq id) and (Posts.profileId eq authProfile.id )
            }.firstOrNull()
        } ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        transaction {
            Posts.update({ Posts.id eq id }) {
                // title이 null 또는 "" 아니면
                // 값이 존재하면 수정
                if(!request.title.isNullOrEmpty()) {
                    it[title] = request.title
                }
                if(!request.content.isNullOrEmpty()) {
                    it[content] = request.content
                }
            }
        }

        return ResponseEntity.ok().build();
    }



    // POST /posts/with-file
    // content-type: multipart/form-data
    // body: files=[...octet-stream]
    //       title=제목입니다.&content=내용입니다.
    @PostMapping("/with-file")
    fun createWithFile(@RequestParam files: Array<MultipartFile>,
                       @RequestParam title: String,
                       @RequestParam content: String) : ResponseEntity<PostWithFileResponse> {
        println("title: $title")
        println("content: $content")

       
        //private val POST_FILE_PATH = "files/post";
        val dirPath = Paths.get(POST_FILE_PATH)  // files/post로 디렉터리 디렉션 지정
        if (!Files.exists(dirPath)) {   //해당 경로 존재하는 지 확인 후 없으면 디렉터리 생성
//            없으면 폴더 생성
            Files.createDirectories(dirPath)
        }

//      mutableListOf = 변경 가능한 리스트 생성하는 함수, 리스트의 요소를 추가,수정 삭제할 수 있는 리스트
        val filesList = mutableListOf<Map<String, String?>>()

        //RunBlocking=  비동기 코드를 동기적으로 실행하기 위한 코루틴빌더 중 하나, 코루틴을 생성하고 실행하는 함수.
        runBlocking {
            // files.forEach로 파일들을 반복하면서, 각 파일에 대해 launch를 사용하여 병렬로 코루틴을 실행
            files.forEach {
                launch {
                    //프론트에서 서버로 파일을 전송할때 각 파일의 정보는 originalFilename,uuidFileName,contentType을 프로퍼티로 가지고 있음.
                    println("filename: ${it.originalFilename}")  //it은 참조하는 객체를 가리킴 지금에서는 매개변수를 받는 file을 참조함
                    // files/post/1.png
                    // 339993-392039-a9d9d9-d9d9d9d.png

                    val uuidFileName =
                        // 파일명
                        buildString { // 문자열을 구성하기 위한 함수, +의 성능 문제를 해결하고자 고안됨 쉽게 문자열 합치기
                            append(UUID.randomUUID().toString()) // UUID: 랜덤하게 문자열로 id값을 만들 수 있는 방법(순서X), ex : 550e8400-e29b-41d4-a716-446655440000
                            append(".") // . 추가
                            append(it.originalFilename!!.split(".").last())  //23fwe-13lfaw-324.jpg = .이후의 확장자명 가져오기 (.jpg , mp4 이 부분 잘라오기)
//                            
                        }

//                  프론트에서 서버로 요청을 보낼때 http://localhost:8080/posts/files/uuidFileName 엔드포인트로 요청
//                    inputStream은 프론트엔드에서 서버로 보낸 데이터를 사용하는데 사용
                    val filePath = dirPath.resolve(uuidFileName)
                    // 파일객체에서 스트림객체를 얻어옴
                    // 스트림객체: 바이트배열 처리할 수 있는 객체
//                  use 함수를 사용하면 스트림을 열고 작업을 수행한 후 자동으로 스트림을 닫아줌.
                    it.inputStream.use {  //it.inputStream은 @RequestParam files을 참조
                        // filePath에 파일 저장, StandardCopyOption.REPLACE_EXISTING = 해당 경로에 동일한 파일 존재하면 덮어쓰기
                        Files.copy(it, filePath, StandardCopyOption.REPLACE_EXISTING)
                    }
                    // 메타데이터 = 파일의 내용 + 파일의 정보를 포함, 파일의 크기,생성일,수정,저자,암호화여부 등
                    // 파일의 메타데이터를 리스트-맵에 임시저장
                    filesList.add(mapOf("uuidFileName" to uuidFileName,
                        "contentType" to it.contentType,
                        "originalFileName" to it.originalFilename))
                }
            }
        }


        // 데이터베이스에 파일정보와 데이터를 저장하고
        // post insert, post_file insert batch
        val result = transaction {
            val p = Posts
            val pf = PostFiles


            // 포스트 1건 저장
            val insertedPost = p.insert {
                it[this.title] = title
                it[this.content] = content
                it[this.profileId] = 1 // 강제로 1번 유저로
                it[this.createdDate] = LocalDateTime.now()
            }.resultedValues!!.first()

            // inser into ... values(..), (..), (..);
            // 참고로 batchInsert를하면 db 설정 상관없이 Expose 로그에는 insert구문이 여러개생김
            // 실제 작동은 batch insert로 처리됨
            // https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert
            pf.batchInsert(filesList) {
                this[pf.postId] = insertedPost[p.id] // 외래키값 넣어줌
                this[pf.contentType] = it["contentType"] as String
                this[pf.originalFileName] = it["originalFileName"] as String
                this[pf.uuidFileName] = it["uuidFileName"] as String
            }

            // insert한 파일 목록을 post id로 조회
            val insertedPostFiles = pf.select { pf.postId eq insertedPost[p.id] }.map {r ->
                PostFileResponse(
                    id = r[pf.id].value,
                    postId = insertedPost[p.id],
                    uuidFileName = r[pf.uuidFileName],
                    originalFileName = r[pf.originalFileName],
                    contentType = r[pf.contentType]
                )
            }

            return@transaction PostWithFileResponse(
                id = insertedPost[p.id],
                title = insertedPost[p.title],
                content = insertedPost[p.content],
                createdDate = insertedPost[p.createdDate].toString(),
                files = insertedPostFiles
            )
        }

        // 응답
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    @GetMapping("/paging/search/with-files")
    fun searchPagingWithFile(@RequestParam size : Int, @RequestParam page : Int, @RequestParam keyword : String?) : Page<PostWithFileResponse>

            = transaction(Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true) {
        // 검색 조건 생성
        val query = when {
            keyword != null -> Posts.select {
                (Posts.title like "%${keyword}%") or
                        (Posts.content like "%${keyword}%" ) }
            else -> Posts.selectAll()

        }

        // 전체 결과 카운트
        val totalCount = query.count()
        println("------------------------------")
        println(size)
        println(page)
        println(keyword)

        // 페이징 조회
        val postsResponse = query
            .orderBy(Posts.id to SortOrder.DESC)
            .limit(size, offset= (size * page).toLong())
            .map { r ->
                PostResponse(r[Posts.id],
                    r[Posts.title],
                    r[Posts.content], r[Posts.createdDate].toString())
            }

        val pf = PostFiles;
        // select * from post_file where post_id in (1,2,4,5,6....)
        val postFilesResponse = pf.select { pf.postId inList postsResponse.map { it.id }.toList() }.map {r ->
            PostFileResponse(
                id = r[pf.id].value,
                postId = r[pf.postId],
                uuidFileName = r[pf.uuidFileName],
                originalFileName = r[pf.originalFileName],
                contentType = r[pf.contentType]
            )
        }

        // 파일목록까지 포함된 리스트로 변환
        val postWithFileResponse = postsResponse.map { p ->
            PostWithFileResponse(
                id=p.id,
                title=p.title,
                content=p.content,
                createdDate = p.createdDate,
                // 각 포스트별 id가 매칭되는 파일만 필터
                files=postFilesResponse.filter { f -> f.postId == p.id }
            )
        }

        // Page 객체로 리턴
        PageImpl(postWithFileResponse, PageRequest.of(page, size),  totalCount)
    }

    // GET /posts/files/0bbbd06e-cd34-461e-8f65-9c5587e54260.mp4
    // Content-Type: video/mp4
    @GetMapping("/files/{uuidFilename}")
    fun downloadFile(@PathVariable uuidFilename : String) : ResponseEntity<Any> {
        // 서버에서 해당 경로의 파일을 읽어오기
        val file = Paths.get("$POST_FILE_PATH/$uuidFilename").toFile()
        // 파일이 없으면 not-found
        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        // 파일의 content-type 처리
        val mimeType = Files.probeContentType(file.toPath())
        val mediaType = MediaType.parseMediaType(mimeType)

        // file:files/post/dkdkdkd.png
        val resource = resourceLoader.getResource("file:$file")
        return ResponseEntity.ok()
            .contentType(mediaType) // video/mp4, image/png, image/jpeg
            .body(resource)
    }

}