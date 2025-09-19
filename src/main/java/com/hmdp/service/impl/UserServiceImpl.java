package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RegexUtils.isPhoneInvalid;
import static net.sf.jsqlparser.parser.feature.Feature.insert;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;



    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号
        if (isPhoneInvalid(phone)) return Result.fail("手机号格式不对");

        //生成验证码
        String code= RandomUtil.randomNumbers(6);

        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+"{"+phone+"}",code,2, TimeUnit.MINUTES);

        log.info("phone number :{}, check code {}",phone,code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone= loginForm.getPhone();

        if (isPhoneInvalid(phone)) return Result.fail("手机号格式不对");

        String code= loginForm.getCode();

        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + "{" + phone + "}");

        if (code==null || !code.equals(cacheCode)){
            return Result.fail("验证码不正确");
        }

        log.info("code match {}",phone);

        User user = query().eq("phone",phone).one();

        if (null==user) {
            user=registerUser(phone);
        }

        // 假设 user 是登录成功的用户对象（User 实体）
        // Step 1: 随机生成 token
        String token = UUID.randomUUID().toString(true); // Hutool 的 UUID 工具也可以

        // Step 2: 复制为轻量级对象（DTO）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // Step 3: 转成 Map（field -> value）形式
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? "" : fieldValue.toString())
        );

        // Step 4: 构建 Redis key
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;

        // Step 5: 存入 Redis 的 Hash 结构
        redisTemplate.opsForHash().putAll(tokenKey, userMap);

        // Step 6: 设置 token 有效期（比如 30 分钟）
        redisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User registerUser(String phone){
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomNumbers(10));
        boolean saveR = save(user);

        return user;

    }


}
