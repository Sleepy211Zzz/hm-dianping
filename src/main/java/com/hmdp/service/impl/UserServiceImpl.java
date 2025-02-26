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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final String LOGIN_CODE_KEY = "login:code:";
    private final int LOGIN_CODE_TTL = 2;
    private final String LOGIN_TOKEN = "login:token:";
    private final int LOGIN_TOKEN_TTL = 30;

    @Override
    public Result sendCode(String phone) {
        if(!RegexUtils.isPhoneInvalid(phone)) return Result.fail("手机号格式错误");
        String validRandomNumbers = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, validRandomNumbers, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功");
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if(!RegexUtils.isPhoneInvalid(phone)) return Result.fail("手机号格式错误");
        String validcode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(code == null || !code.equals(validcode)) return Result.fail("验证码错误");
        User user = query().eq("phone", phone).one();
        if(user == null){
            user = createUserWithPhone(phone);
        }
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        stringRedisTemplate.opsForHash().putAll(LOGIN_TOKEN + token, BeanUtil.beanToMap(userDTO));
        stringRedisTemplate.expire(LOGIN_TOKEN + token, LOGIN_TOKEN_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        val userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keyPre = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keyPre;
        int dayOfMonth = now.getDayOfMonth() - 1;
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        val userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keyPre = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keyPre;
        int dayOfMonth = now.getDayOfMonth() - 1;
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(result == null || result.isEmpty()){
            return Result.ok(0);
        }

        Long number = result.get(0);

        if(number == null || number == 0){
            return Result.ok(0);
        }

        int count = 0;

        while (number != 0){
            if((number & 1) == 1){
                count++;
            }
            number >>>= 1;
        }

        return Result.ok(count);
    }


    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
