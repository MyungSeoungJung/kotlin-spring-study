package com.example.myapp.post

import com.example.myapp.auth.Auth
import com.example.myapp.auth.AuthProfile
import com.example.myapp.auth.Profiles
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
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
import java.sql.Connection
import java.time.LocalDateTime

@RestController
@RequestMapping("posts")
class PostController {

    // exposed selectAll -> List<ResultRow>
    // ResultRow는 transaction {} 구문 밖에서 접근 불가능함
    // transaction 구분 외부로 보낼 때는 별도의 객체로 변환해서 내보낸다.
    // 결과값: List<PostResponse>
    @GetMapping
    fun fetch() = transaction {
        Posts.selectAll().map { r -> PostResponse(
            r[Posts.id], r[Posts.title], r[Posts.content],
            r[Posts.createdDate].toString())
        }
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
}