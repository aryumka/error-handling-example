package com.example.core

interface ResultError {
    val code: String
    val msg: String
}

interface BusinessError : ResultError
