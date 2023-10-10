package com.example.myapp.auth

import com.example.myapp.auth.util.JwtUtil
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.jetbrains.exposed.sql.Database
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.*

@RestController
@RequestMapping("/auth")
class AuthController(private val service: AuthService) {
    @PostMapping(value = ["/signup"])
    fun signUp(@RequestBody req: SignupRequest): ResponseEntity<Long> {
        println(req)

        // 1. Validation
        // 입력값 검증
        // 패스워드없거나, 닉네임, 이메일 없음...
        // 필수값은 SingupRequest에서 자동으로 검증

        // 2. Buisness Logic(데이터 처리)
        // profile, login 생성 트랜잭션 처리
        val profileId = service.createIdentity(req)

        if(profileId > 0) {
            // 3. Response
            // 201: created
            return ResponseEntity.status(HttpStatus.CREATED).body(profileId)
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(profileId)
        }
    }

    //1. (브라우저) 로그인 요청
    // [RequestLine]
    //   HTTP 1.1 POST 로그인주소
    // [RequestHeader]
    //   content-type: www-form-urlencoded
    // [Body]
    //   id=...&pw=...
    //2. (서버) 로그인 요청을 받고 인증처리 후 쿠키 응답 및 웹페이지로 이동
    // HTTP Status 302 (리다이렉트)
    // [Response Header]
    //   Set-Cookie: 인증키=키........; domain=.naver.com
    //   Location: "리다이렉트 주소"
    //3. (브라우저) 쿠키를 생성(도메인에 맞게)
    @PostMapping(value = ["/signin"])
    fun signIn(
        @RequestParam username: String,
        @RequestParam password: String,
        res: HttpServletResponse,
    ): ResponseEntity<*> {
        println(username)
        println(password)

        val (result, message) = service.authenticate(username, password)

        if(result) {
            // 3. cookie와 헤더를 생성한후 리다이렉트
            val cookie = Cookie("token", message)
            cookie.path = "/"
            cookie.maxAge = (JwtUtil.TOKEN_TIMEOUT / 1000L).toInt() // 만료시간
            cookie.domain = "localhost" // 쿠키를 사용할 수 있 도메인

            // 응답헤더에 쿠키 추가
            res.addCookie(cookie)

            // 웹 첫페이지로 리다이렉트
            return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(
                    ServletUriComponentsBuilder
                        .fromHttpUrl("http://localhost:5500")
                        .build().toUri()
                )
                .build<Any>()
        }

        // 오류 메시지 반환
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(
                ServletUriComponentsBuilder
                    .fromHttpUrl("http://localhost:5500/login.html?err=$message")
                    .build().toUri()
            )
            .build<Any>()
    }
}