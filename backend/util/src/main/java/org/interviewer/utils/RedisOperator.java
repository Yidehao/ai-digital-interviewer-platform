package org.interviewer.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis utility class
 */
@Component
public class RedisOperator {
	
	@Autowired
	private StringRedisTemplate redisTemplate;

	// Key operations, simple key-value operations

	/**
	 * Check if key exists
	 * @param key
	 * @return
	 */
	public boolean keyIsExist(String key) {
		return redisTemplate.hasKey(key);
	}

	/**
	 * Implement command: TTL key, returns the remaining time to live (TTL) of the given key in seconds
	 * 
	 * @param key
	 * @return
	 */
	public long ttl(String key) {
		return redisTemplate.getExpire(key);
	}
	
	/**
	 * Implement command: expire, set expiration time in seconds
	 * 
	 * @param key
	 * @return
	 */
	public void expire(String key, long timeout) {
		redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
	}
	
	/**
	 * Implement command: increment key, increment key once
	 * 
	 * @param key
	 * @return
	 */
	public Long increment(String key, long delta) {
		return redisTemplate.opsForValue().increment(key, delta);
	}

	/**
	 * Increment using hash
	 */
	public long incrementHash(String name, String key, long delta) {
		return redisTemplate.opsForHash().increment(name, key, delta);
	}

	/**
	 * Decrement using hash
	 */
	public long decrementHash(String name, String key, long delta) {
		delta = delta * (-1);
		return redisTemplate.opsForHash().increment(name, key, delta);
	}

	/**
	 * Hash set value
	 */
	public void setHashValue(String name, String key, String value) {
		redisTemplate.opsForHash().put(name, key, value);
	}

	/**
	 * Hash get value
	 */
	public String getHashValue(String name, String key) {
		return (String)redisTemplate.opsForHash().get(name, key);
	}

	/**
	 * Implement command: decrement key, decrement key once
	 *
	 * @param key
	 * @return
	 */
	public long decrement(String key, long delta) {
		return redisTemplate.opsForValue().decrement(key, delta);
	}

	/**
	 * Implement command: KEYS pattern, find all keys matching the given pattern
	 */
	public Set<String> keys(String pattern) {
		return redisTemplate.keys(pattern);
	}

	/**
	 * Implement command: DEL key, delete a key
	 * 
	 * @param key
	 */
	public void del(String key) {
		redisTemplate.delete(key);
	}

	/**
	 * Redis full cache deletion
	 * @param key can pass one value or multiple
	 */
	public void allDel(String key) {
		Set<String> keys = redisTemplate.keys(key + "*");
		redisTemplate.delete(keys);
	}

	// String operations

	/**
	 * Implement command: SET key value, set a key-value (associate string value to key)
	 * 
	 * @param key
	 * @param value
	 */
	public void set(String key, String value) {
		redisTemplate.opsForValue().set(key, value);
	}

	/**
	 * Implement command: SET key value EX seconds, set key-value and timeout (seconds)
	 * 
	 * @param key
	 * @param value
	 * @param timeout
	 *            (in seconds)
	 */
	public void set(String key, String value, long timeout) {
		redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
	}

	/**
	 * If key does not exist, set it; if it exists, do nothing
	 * @param key
	 * @param value
	 */
	public Boolean setnx60s(String key, String value) {
		return redisTemplate.opsForValue().setIfAbsent(key, value, 60, TimeUnit.SECONDS);
	}

	/**
	 * If key does not exist, set it; if it exists, throw error
	 * @param key
	 * @param value
	 */
	public Boolean setnx(String key, String value) {
		return redisTemplate.opsForValue().setIfAbsent(key, value);
	}

	public Boolean setnx(String key, String value, Integer seconds) {
		return redisTemplate.opsForValue().setIfAbsent(key, value, seconds, TimeUnit.SECONDS);
	}

	public Boolean setHashNX(String key, String hashKey, String value) {
		return redisTemplate.opsForHash().putIfAbsent(key, hashKey, value);
	}

	/**
	 * Implement command: GET key, return the string value associated with key
	 * 
	 * @param key
	 * @return value
	 */
	public String get(String key) {
		return (String)redisTemplate.opsForValue().get(key);
	}

	/**
	 * Batch query, corresponding to mget
	 * @param keys
	 * @return
	 */
	public List<String> mget(List<String> keys) {
		return redisTemplate.opsForValue().multiGet(keys);
	}

	/**
	 * Batch query, pipeline
	 * @param keys
	 * @return
	 */
	public List<Object> batchGet(List<String> keys) {

//		nginx -> keepalive
//		redis -> pipeline

		List<Object> result = redisTemplate.executePipelined(new RedisCallback<String>() {
			@Override
			public String doInRedis(RedisConnection connection) throws DataAccessException {
				StringRedisConnection src = (StringRedisConnection)connection;

				for (String k : keys) {
					src.get(k);
				}
				return null;
			}
		});

		return result;
	}


	// Hash operations

	/**
	 * Implement command: HSET key field value, set the value of field in hash table key to value
	 * 
	 * @param key
	 * @param field
	 * @param value
	 */
	public void hset(String key, String field, Object value) {
		redisTemplate.opsForHash().put(key, field, value);
	}

	/**
	 * Implement command: HGET key field, return the value of the given field in hash table key
	 * 
	 * @param key
	 * @param field
	 * @return
	 */
	public String hget(String key, String field) {
		return (String) redisTemplate.opsForHash().get(key, field);
	}

	/**
	 * Implement command: HDEL key field [field ...], delete one or more specified fields from hash table key, non-existent fields will be ignored
	 * 
	 * @param key
	 * @param fields
	 */
	public void hdel(String key, Object... fields) {
		redisTemplate.opsForHash().delete(key, fields);
	}

	/**
	 * Implement command: HGETALL key, return all fields and values in hash table key
	 * 
	 * @param key
	 * @return
	 */
	public Map<Object, Object> hgetall(String key) {
		return redisTemplate.opsForHash().entries(key);
	}

	// List operations

	/**
	 * Implement command: LPUSH key value, insert a value into the head of list key
	 * 
	 * @param key
	 * @param value
	 * @return The length of the list after executing LPUSH command
	 */
	public long lpush(String key, String value) {
		return redisTemplate.opsForList().leftPush(key, value);
	}

	/**
	 * Implement command: LPOP key, remove and return the head element of list key
	 * 
	 * @param key
	 * @return The head element of list key
	 */
	public String lpop(String key) {
		return (String)redisTemplate.opsForList().leftPop(key);
	}

	/**
	 * Implement command: RPUSH key value, insert a value into the tail (rightmost) of list key
	 * 
	 * @param key
	 * @param value
	 * @return The length of the list after executing LPUSH command
	 */
	public long rpush(String key, String value) {
		return redisTemplate.opsForList().rightPush(key, value);
	}

	/**
	 * Delete lock
	 * Atomic guarantee
	 * @param script
	 * @param key
	 * @param value
	 */
	public Long execLuaScript(String script, String key, String value) {
		return redisTemplate.execute(
						new DefaultRedisScript<>(script, Long.class),
		//				Arrays.asList(key),
						Collections.singletonList(key),
						value
				);
	}

}