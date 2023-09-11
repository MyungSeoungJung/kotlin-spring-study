package com.example.myapp.auth.util

import at.favre.lib.crypto.bcrypt.BCrypt

object HashUtil {
    fun createHash(cipherText: String): String {
        // https://www.baeldung.com/java-password-hashing
        // https://mia-dahae.tistory.com/120

        // https://blog.kakaocdn.net/dn/IdglW/btrEoJ6HDUJ/FmJqCChB9NCXd6fapmJdAk/img.png
        // 'hashToString': Salt와 함께 해시를 생성
        // hash create 할때는 salt를 랜덤으로 생성해서 저장
        return BCrypt
            .withDefaults()
            .hashToString(12, cipherText.toCharArray())
    }

    // 문자열을 받아서 salt와 함께 hash가 맞는지 확인
// ciphertext(암호화안된 문자열)
// plaintext(구조가없는 문자열)
// hash: $버전$라운드횟수$22바이트salt+해시문자열
    fun verifyHash(ciphertext: String, hash: String): Boolean {
        // hash를 verifying 할 때는 이미 있는 salt값으로
        // ciphertext를 결합하여 hash와 맞는지 확인
        return BCrypt
            .verifyer()
            .verify(ciphertext.toCharArray(), hash).verified
    }
}