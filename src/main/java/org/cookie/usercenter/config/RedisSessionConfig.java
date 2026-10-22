package org.cookie.usercenter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/** 正式/本地环境都用已有 Redis；test 使用内存仓库，不接触业务 Redis。 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800, redisNamespace = "user-center:session")
public class RedisSessionConfig { }
