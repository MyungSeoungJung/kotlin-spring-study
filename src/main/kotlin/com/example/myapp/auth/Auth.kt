package com.example.myapp.auth

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Auth( // 역할(일반사용자, 골드사용자, 관리자, 판매관리자)..
    // @Auth(role="GOLD")
    //    public String role();
    val require: Boolean = true
)
