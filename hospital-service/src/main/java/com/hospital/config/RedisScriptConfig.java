package com.hospital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis Lua 脚本配置。
 * 库存扣减的"判断 + 扣减"两步写在一个 Lua 脚本里，Redis 单线程执行时中间不被打断，
 * 从根上消除"查完再扣"之间的并发缝隙。
 */
@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> stockDeductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> stockRepayScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/stock_repay.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
