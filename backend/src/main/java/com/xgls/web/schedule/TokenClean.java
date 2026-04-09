package com.xgls.web.schedule;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xgls.web.entity.User;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TokenClean {
    @Autowired
    UserService userService;
    @Autowired
    RedisService redisService;

    @Scheduled(cron = "0 5 * * * ?")
    public void clean() {
        List<User> user = userService.list();
        for (int i = 0; i < user.size(); i++) {
            Long id = user.get(i).getId();
            String name = user.get(i).getUsername();
            Set<Object> set = redisService.getUserJwt(id);
            if (set != null) {
                log.info("userId:{},username:{},size:{}", id, name, set.size());
                for (Object item : set) {
                    String token = item.toString();
                    if (redisService.getJwtToken(token) == null) {
                        redisService.removeUserJwt(id, token);
                    }
                }
            }
        }
    }
}
