package com.hmdp.config;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Sunyur
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenInterceptor.class);

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*// 获取session
        HttpSession session = request.getSession();*/
        // TODO：获取请求头里的token
        String token = request.getHeader("authorization");

        if (StrUtil.isBlank(token)) {
            // token为空，直接放行
            return true;
        }
        // TODO：基于token从redis获取用户(Hash数据) -> 转为UserDTO对象
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            // 从redis中读取用户是否存在，如果不存在，没办法刷新，放！
            return true;
        }
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 存在，放行 -> 存入LocalThread
        UserHolder.saveUser(user);
        log.info("save user! param is {}", UserHolder.getUser());

        // TODO：刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, SECONDS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
        log.info("remove User...");
    }
}
