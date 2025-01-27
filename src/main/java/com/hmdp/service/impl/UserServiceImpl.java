package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(!RegexUtils.isPhoneInvalid(phone)) return Result.fail("手机号格式错误");
        String validRandomNumbers = RandomUtil.randomNumbers(6);
        session.setAttribute("code", validRandomNumbers);
        log.debug("发送短信验证码成功");
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if(!RegexUtils.isPhoneInvalid(phone)) return Result.fail("手机号格式错误");
        String validcode = session.getAttribute("code").toString();
        if(code == null || !code.equals(validcode)) return Result.fail("验证码错误");
        User user = query().eq("phone", phone).one();
        if(user == null){
            user = createUserWithPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
