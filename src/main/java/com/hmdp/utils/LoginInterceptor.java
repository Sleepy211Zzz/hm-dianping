package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * ClassName: LoginInterceptor
 * Description:
 * Author
 * Create 2025/1/27 20:24
 * VERSION 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        if(user == null){
            response.setStatus(401);
            return false;
        }
        UserHolder.saveUser((UserDTO) user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex){

    }
}
