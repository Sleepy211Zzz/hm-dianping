package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: LoginInterceptor
 * Description:
 * Author
 * Create 2025/1/27 20:24
 * VERSION 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        String token = request.getHeader("authorization");
        if(token == null){
            response.setStatus(401);
            return false;
        }
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(token);
        if(entries.isEmpty()){
            response.setStatus(401);
            return false;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex){

    }
}
