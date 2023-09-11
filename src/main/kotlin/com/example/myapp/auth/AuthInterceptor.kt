package com.example.myapp.auth

import com.example.myapp.auth.util.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.lang.reflect.Method

@Component
class AuthInterceptor : HandlerInterceptor {
    // 토큰 검증 및 subject/claim(토큰 내부의 데이터)를 객체화
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // 1. 요청을 처리할 컨트롤 메서드에 @Auth 어노테이션이 있는지 확인
        // HTTP요청을 처리하는 메서드인지 확인
        if (handler is HandlerMethod) {
            val handlerMethod: HandlerMethod = handler
            val method: Method = handlerMethod.method
            println(method)
            // @Auth 어노테이션이 있는지 확인
            if(method.getAnnotation(Auth::class.java) == null) {
                // @Auth 어노테이션이 없으면 토큰 관련 처리를 별도로 하지 않음
                return true
            }

            // 2. 인증토큰 읽어오기
            // @Auth 어노테이션이 있는 경우
            // Request Header에 authorization 헤더에 토큰을 조회
            val token: String = request.getHeader("Authorization")
            println(token)
            // 인증 토큰이 없으면
            if (token.isEmpty()) {
                // 401: Unauthorized(미인가인데, 미인증이라는 의미로 사용)
                // 인증토큰이 없음
                response.status = 401
                return false
            }

            // 인증 토큰이 있으면
            // 3. 인증토큰 검증 및 페이로드(subject/claim) 객체화하기
            // 메시지 개념에서 서로 주고받는 데이터를 페이로드(payload)
            val profile: AuthProfile? = JwtUtil.validateToken(token.replace("Bearer ", ""))
            if (profile == null) {
                // 401: Unauthorized
                // 인증토큰이 잘못 됨(시그니처, 페이로드, 알고리즘..)
                response.status = 401
                return false
            }

            println(profile)
            // 4. 요청 속성(attribute)에 프로필 객체 추가하기
            request.setAttribute("authProfile", profile)
            return true
        }
        return true
    }
}