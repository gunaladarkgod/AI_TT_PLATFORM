package com.xgls.web.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.User;
import com.xgls.web.mapper.UserMapper;
import com.xgls.web.service.UserService;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {

}
