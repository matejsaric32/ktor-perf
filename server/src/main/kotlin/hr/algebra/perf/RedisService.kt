package hr.algebra.perf

import kotlinx.serialization.json.Json
import redis.clients.jedis.Jedis

class RedisService(
    private val host : String = "localhost",
    private val port : Int = 6379
) {
    private val jedis = Jedis(host, port)
    val json = Json { ignoreUnknownKeys = true }
    
    fun get(key : String) : String? = jedis[key]
    
    fun set(key : String, value : String, ttlSeconds : Int = 300) {
        jedis.setex(key, ttlSeconds.toLong(), value)
    }
    
    inline fun <reified T> getObject(key : String) : T? {
        val value = get(key) ?: return null
        return json.decodeFromString<T>(value)
    }
    
    inline fun <reified T> setObject(key : String, obj : T, ttlSeconds : Int = 300) {
        val value = json.encodeToString(obj)
        set(key, value, ttlSeconds)
    }
    
    fun close() = jedis.close()
}