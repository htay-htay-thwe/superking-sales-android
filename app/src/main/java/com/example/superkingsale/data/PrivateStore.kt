package com.example.superkingsale.data

interface PrivateStore {
    fun get(name: String): String?
    fun put(name: String, value: String?)
    fun clear()
}
