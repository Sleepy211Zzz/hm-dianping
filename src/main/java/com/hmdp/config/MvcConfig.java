package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.ReflishTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ClassName: MvcConfig
 * Description:
 * Author
 * Create 2025/1/27 20:31
 * VERSION 1.0
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns("/user/login", "/user/code", "/blog/hot", "/shop/**", "/shot-type/**").order(1);
        registry.addInterceptor(new ReflishTokenInterceptor(stringRedisTemplate)).excludePathPatterns("/**").order(0);
    }
}
