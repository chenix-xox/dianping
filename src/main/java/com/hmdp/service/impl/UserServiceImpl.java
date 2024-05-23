package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 验证手机号是否符合规范
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 返回错误信息
            return Result.fail(PHONE_IS_INVALID);
        }

        // 生成随机验证码
        String code = RandomUtil.randomNumbers(6);

       /* // 存储到session
        session.setAttribute(PHONE_AUTH_CODE, code);*/

        // 存储到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 发送短信验证码 - 直接打印 - 后期调用发送短信的平台
        log.debug("您的短信验证码为：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(PHONE_IS_INVALID);
        }

        /*// 手机号验证通过，校验验证码
        String cacheCode = (String) session.getAttribute(PHONE_AUTH_CODE);*/
        // TODO:改为从redis获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 获取session中的验证码为null
            // or 此次发送的验证码和session存储的验证码不同
            // 返回验证码错误信息
            return Result.fail("验证码错误");
        }

        // 根据手机号查询该用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 不存在就创建一个
            user = createUserWithPhone(phone);
        }

        // TODO：存入session -> 存入redis
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 生成token
        String token = UUID.randomUUID().toString(true);
        // User转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 存储到redis
        String tokenKey = LOGIN_USER_KEY + token;
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions
                        .create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldKey, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

}
