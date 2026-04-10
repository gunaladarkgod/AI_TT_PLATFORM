package com.xgls.web.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.filter.InvalidRequestFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xgls.web.security.JwtFilter;
import com.xgls.web.security.JwtRealm;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ShiroConfig {

    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(DefaultWebSecurityManager defaultWebSecurityManager) {
        ShiroFilterFactoryBean bean = new ShiroFilterFactoryBean();
        bean.setSecurityManager(defaultWebSecurityManager);

        // 过滤器集合
        Map<String, Filter> filters = new LinkedHashMap<>();
        filters.put("invalidRequest", invalidRequestFilter()); // 允许中文路径
        filters.put("jwt", new JwtFilter());
        bean.setFilters(filters);

        boolean knifeProd = SpringUtil.getProperty("knife4j.production", Boolean.TYPE, false);
        boolean enableToken = Boolean.parseBoolean(SpringUtil.getProperty("sys.enable-token", "false"));
        log.info("knife4j.production: {}", knifeProd);
        log.info("sys.enable-token: {}", enableToken);

        // **关键：必须是 LinkedHashMap 才能保证匹配顺序**
        Map<String, String> chain = new LinkedHashMap<>();

        // swagger 放行（非生产）
        if (!knifeProd) {
            chain.put("/swagger**/**", "anon");
            chain.put("/webjars/**", "anon");
            chain.put("/v3/**", "anon");
            chain.put("/doc.html", "anon");
        }

        // 业务放行
        chain.put("/auth/**", "anon");
        chain.put("/sse/**", "anon");
        chain.put("/working/**", "anon");
        chain.put("/api/**", "anon");
        chain.put("/dist/**", "anon");
        chain.put("/", "anon");

        // 原始数据集预览放行
        chain.put("/original-dataset/**", "anon");
        chain.put("/taskDataset/**", "anon");
        // === 新增：任务数据集子集预览 & 图片放行（关键） ===
        // /taskDataset/{id}/subset-preview?subset=core&perLabel=3
        chain.put("/taskDataset/*/subset-preview", "anon");
        // /taskDataset/{id}/subset-image?subset=core&img=xxx
        chain.put("/taskDataset/*/subset-image", "anon");
        // ===============================================

//  新增：实例数据集预览 / 图片 / 标注
        chain.put("/instanceDataset/**", "anon");

        // ！！最后一条兜底规则必须在末尾！！
        chain.put("/**", enableToken ? "jwt" : "anon");

        bean.setFilterChainDefinitionMap(chain);
        return bean;
    }

    @Bean
    public DefaultWebSecurityManager defaultWebSecurityManager(JwtRealm jwtRealm) {
        DefaultWebSecurityManager mgr = new DefaultWebSecurityManager();
        mgr.setRealm(jwtRealm);
        // 关闭 session（JWT 无状态）
        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        DefaultSessionStorageEvaluator sse = new DefaultSessionStorageEvaluator();
        sse.setSessionStorageEnabled(false);
        subjectDAO.setSessionStorageEvaluator(sse);
        mgr.setSubjectDAO(subjectDAO);
        return mgr;
    }

    @Bean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator ap = new DefaultAdvisorAutoProxyCreator();
        ap.setProxyTargetClass(true);
        return ap;
    }

    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(
            DefaultWebSecurityManager defaultWebSecurityManager) {
        AuthorizationAttributeSourceAdvisor adv = new AuthorizationAttributeSourceAdvisor();
        adv.setSecurityManager(defaultWebSecurityManager);
        return adv;
    }

    @Bean
    public LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    // 解决中文路径报 400
    @Bean
    public InvalidRequestFilter invalidRequestFilter() {
        InvalidRequestFilter f = new InvalidRequestFilter();
        f.setBlockNonAscii(false);
        return f;
    }
}
