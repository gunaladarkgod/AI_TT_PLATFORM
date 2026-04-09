package com.xgls.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import cn.hutool.system.oshi.OshiUtil;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;
import oshi.hardware.ComputerSystem;

@SpringBootApplication
@MapperScan({ "com.xgls.web.mapper" })
@ServletComponentScan
@EnableScheduling
@EnableTransactionManagement
@EnableAsync
@Import(cn.hutool.extra.spring.SpringUtil.class)
@Slf4j
public class WebApplication {

	public static void main(String[] args) {
		getDevInfos();
		SpringApplication.run(WebApplication.class, args);
	}

	@Bean
	public CorsFilter corsFilter() {
		// 1. 创建 CorsConfiguration 对象后添加配置
		CorsConfiguration configuration = new CorsConfiguration();
		// 设置放行那些域
		configuration.addAllowedOriginPattern("*");
		// configuration.addAllowedOrigin("*");//过时写法
		// 放行那些原始请求头部信息
		configuration.addAllowedHeader("*");
		// 暴露那些头部信息
		configuration.addExposedHeader("*");
		// 放行那些请求方式
		configuration.addAllowedMethod("GET"); // get
		configuration.addAllowedMethod("POST"); // post
		configuration.addAllowedMethod("PUT"); // put
		configuration.addAllowedMethod("DELETE"); // delete
		configuration.addAllowedMethod("*"); // 放行全部请求
		// 是否发送 Cookie
		configuration.setAllowCredentials(false);
		// 2. 添加映射路径
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		// 3. 返回 CorsFilter
		return new CorsFilter(source);
	}

	public static void getDevInfos() {
		try {
			ComputerSystem sys = OshiUtil.getSystem();
			log.info("getHardwareUUID:{}", sys.getHardwareUUID());
			log.info("getManufacturer:{}", sys.getManufacturer());
			log.info("getModel:{}", sys.getModel());
			log.info("getSerialNumber:{}", sys.getSerialNumber());
			log.info("getFirmware:{}", sys.getFirmware());
			log.info("getBaseboard:{}", sys.getBaseboard());

			SystemInfo sys2 = new SystemInfo();
			CentralProcessor processor = sys2.getHardware().getProcessor();
			ProcessorIdentifier cpu = processor.getProcessorIdentifier();
			log.info("Cpu info:{}", cpu);
			log.info("Cpu Id:{}", cpu.getProcessorID());
			log.info("Cpu Name:{}", cpu.getName());
			log.info("Cpu Identifier:{}", cpu.getIdentifier());
			log.info("Cpu Vendor:{}", cpu.getVendor());
		} catch (Exception e) {
			log.warn("get devInfo err:{}", e.getMessage());
		}
	}
}
