package hr.algebra.perf

import hr.algebra.perf.config.RedisConfig
import io.ktor.server.application.Application
import kotlinx.serialization.json.Json
import redis.clients.jedis.Jedis

class RedisService(
    val config : RedisConfig
) {
    private val jedis = Jedis(config.host, config.port)
    val json = Json { ignoreUnknownKeys = true }
    
    fun get(key : String) : String? = jedis[key]
    
    fun set(key : String, value : String, ttlSeconds : Int = config.ttl) {
        jedis.setex(key, ttlSeconds.toLong(), value)
    }
    
    inline fun <reified T> getObject(key: String): T? {
        val value = get(key) ?: return null
        return json.decodeFromString<T>(value)
    }
    
    inline fun <reified T> setObject(key : String, obj : T, ttlSeconds : Int = config.ttl) {
        val value = json.encodeToString<T>(obj)
        set(key, value, ttlSeconds)
    }
    
    fun close() = jedis.close()
}

fun Application.getRedisConfig() : RedisConfig {
    val config = environment.config
    return RedisConfig(
        host = config.property("redis.host").getString(),
        port = config.property("redis.port").getString().toInt(),
        ttl = config.property("redis.ttl").getString().toInt()
    )
}