package com.example.myapp.auth.util

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.example.myapp.auth.AuthProfile
import java.util.*

object JwtUtil {
    // 임의의 서명 값 - 키파일 등을 읽어서 처리가능
    var secret = "your-secret"

    // ms 단위
    val TOKEN_TIMEOUT = (1000 * 60 * 60 * 24 * 7).toLong()
    // JWT 토큰 생성
    fun createToken(id: Long, username: String, nickname: String): String {
        // 토큰 생성시간과 만료시간을 만듦
        val now = Date()
        // 만료시간: 2차인증 이런게 잘걸려있으면 큰문제는 안됨. 내컴퓨터를 다른 사람이 쓴다.
        // 길게: 7일~30일
        // 보통: 1시간~3시간
        // 짧게: 5분~15분
        val exp = Date(now.time + TOKEN_TIMEOUT)
        val algorithm = Algorithm.HMAC256(secret)
        return JWT.create() // sub: 토큰 소유자
            .withSubject(id.toString())
            .withClaim("username", username)
            .withClaim("nickname", nickname)
            .withIssuedAt(now)
            .withExpiresAt(exp)
            .sign(algorithm)
    }

    fun validateToken(token: String): AuthProfile? {
        val algorithm = Algorithm.HMAC256(secret)
        // 검증 객체 생성
        val verifier: JWTVerifier = JWT.require(algorithm).build()
        return try {
            val decodedJWT: DecodedJWT = verifier.verify(token)
            // 토큰 검증 제대로 된 상황
            // 토큰 페이로드(데이터, subject/claim)를 조회
            val id: Long = java.lang.Long.valueOf(decodedJWT.getSubject())
            val nickname: String = decodedJWT
                .getClaim("nickname").asString()
            val username: String = decodedJWT
                .getClaim("username").asString()

            AuthProfile(id, nickname, username)
        } catch (e: JWTVerificationException) {
            // 토큰 검증 오류 상황
            null
        }
    }
}

