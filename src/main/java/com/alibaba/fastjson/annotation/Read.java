package com.alibaba.fastjson.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 已经阅读过的方法
 * @author Kong Lei
 * 2019年9月25日 上午11:49:46
 */
@Retention(RetentionPolicy.SOURCE)
@Target(value= {ElementType.METHOD,ElementType.CONSTRUCTOR})
public @interface Read {
	String desc() default "";
}


