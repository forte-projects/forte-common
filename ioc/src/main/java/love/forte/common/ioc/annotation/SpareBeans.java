/*
 * Copyright (c) 2020. ForteScarlet All rights reserved.
 * Project  parent
 * File     SpareBeans.java
 *
 * You can contact the author through the following channels:
 * github https://github.com/ForteScarlet
 * gitee  https://gitee.com/ForteScarlet
 * email  ForteScarlet@163.com
 * QQ     1149159218
 */

package love.forte.common.ioc.annotation;

import love.forte.common.utils.annotation.AnnotateMapping;

import java.lang.annotation.*;

/**
 *
 * 备用Beans，即默认优先级为最低级的 {@link Beans}。
 *
 * @author <a href="https://github.com/ForteScarlet"> ForteScarlet </a>
 */
@Retention(RetentionPolicy.RUNTIME)    //注解会在class字节码文件中存在，在运行时可以通过反射获取到
@Target({ElementType.TYPE, ElementType.METHOD}) //接口、类、枚举、注解、方法
@Documented
@Beans(init = true)
@AnnotateMapping(value = Beans.class)
public @interface SpareBeans {

    /** 依赖对象的名称，如果没有则以类名取代 */
    String value() default "";

    /** 是否为单例，默认为单例 */
    boolean single() default true;

    /**
     * 是否将类中全部字段标记为Depend，默认为false
     * */
    boolean allDepend() default false;

    /** 当全部标记为@Depend的时候，此参数为所有字段标记的@Depend注解对象，默认为无参注解 */
    Depend depend() default @Depend;


    /** 根据参数类型列表来指定构造函数，默认为无参构造。仅标注在类上的时候有效 */
    Class[] constructor() default {};

    /**
     * 是否在依赖注入流程结束后初始化一次，默认为false，即使用懒加载策略。
     */
    boolean init() default false;

}