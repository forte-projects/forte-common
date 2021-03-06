/*
 * Copyright (c) 2020. ForteScarlet All rights reserved.
 * Project  parent
 * File     AnnotationUtil.java
 *
 * You can contact the author through the following channels:
 * github https://github.com/ForteScarlet
 * gitee  https://gitee.com/ForteScarlet
 * email  ForteScarlet@163.com
 * QQ     1149159218
 */

package love.forte.common.utils.annotation;

import love.forte.common.collections.LRULinkedHashMap;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

/**
 * 对于一些注解的获取等相关的工具类。
 * <p>
 * 注解可以通过 {@link AnnotateMapping} 实现注解间的继承与值映射。
 * <p>
 * 继承即通过注解类标注来实现。例如 {@code @A} 标注在了 {@code @B}上，则认为 {@code @B} 继承了注解 {@code @A}，
 * <p>
 * 则假如一个类上标注了 {@code @B} 的时候，便可以直接通过 {@code AnnotationUtil.getAnnotation(class, A.class)}来获取注解 {@code @A}实例。
 *
 * @author ForteScarlet <[163邮箱地址]ForteScarlet@163.com>
 * @since JDK1.8
 **/
public class AnnotationUtil {

    /**
     * java原生注解所在包路径
     */
    private static final Package JAVA_ANNOTATION_PACKAGE = Target.class.getPackage();


    /**
     * 注解缓存，记录曾经保存过的注解与其所在类
     */
    private static final LRULinkedHashMap<AnnotatedElement, Map<Class<? extends Annotation>, Annotation>> ANNOTATION_CACHE = new LRULinkedHashMap<>(128);

    /**
     * 记录已经证实不存在的注解信息
     */
    private static final LRULinkedHashMap<AnnotatedElement, Set<Class<? extends Annotation>>> NULL_CACHE = new LRULinkedHashMap<>(128);


    private static final Set<String> OBJECT_METHODS;

    static {
        OBJECT_METHODS = new HashSet<>();
        OBJECT_METHODS.add("toString");
        OBJECT_METHODS.add("equals");
        OBJECT_METHODS.add("hashCode");
        OBJECT_METHODS.add("getClass");
        OBJECT_METHODS.add("clone");
        OBJECT_METHODS.add("notify");
        OBJECT_METHODS.add("notifyAll");
        OBJECT_METHODS.add("wait");
        OBJECT_METHODS.add("finalize");
    }

    /**
     * 得到一个注解的默认值代理。可以提供额外的参数。
     *
     * @param annotationType 注解类型。
     * @param params         参数列表。可以为null。
     * @return 此注解的代理实例。
     */
    public static <T extends Annotation> T getDefaultAnnotationProxy(Class<T> annotationType, Map<String, Object> params) {
        if (params == null) {
            params = Collections.emptyMap();
        }
        return AnnotationProxyUtil.proxy(annotationType, params);
    }

    /**
     * 得到一个注解的默认值代理。
     *
     * @param annotationType 注解类型。
     * @return 此注解的代理实例。
     */
    public static <T extends Annotation> T getDefaultAnnotationProxy(Class<T> annotationType) {
        return AnnotationProxyUtil.proxy(annotationType, Collections.emptyMap());
    }


    /**
     * 从某个类上获取注解对象，注解可以深度递归
     * 如果存在多个继承注解，则优先获取浅层第一个注解，如果浅层不存在，则返回第一个获取到的注解
     * 请尽可能保证仅存在一个或者一种继承注解，否则获取到的类型将不可控
     *
     * @param from           获取注解的某个类
     * @param annotationType 想要获取的注解类型
     * @return 获取到的第一个注解对象
     */
    public static <T extends Annotation> T getAnnotation(AnnotatedElement from, Class<T> annotationType) {
        return getAnnotation(from, annotationType, (Class<T>[]) new Class[0]);
    }

    /**
     * 从某个类上获取注解对象，注解可以深度递归
     * 如果存在多个继承注解，则优先获取浅层第一个注解，如果浅层不存在，则返回第一个获取到的注解
     * 请尽可能保证仅存在一个或者一种继承注解，否则获取到的类型将不可控
     *
     * @param from           获取注解的某个类
     * @param annotationType 想要获取的注解类型
     * @param ignored        获取注解列表的时候的忽略列表
     * @return 获取到的第一个注解对象
     */
    @SafeVarargs
    public static <T extends Annotation> T getAnnotation(AnnotatedElement from, Class<T> annotationType, Class<T>... ignored) {
        return getAnnotation(null, from, annotationType, ignored);
    }


    public static boolean containsAnnotation(AnnotatedElement from, Class<? extends Annotation> annotationType) {
        return getAnnotation(from, annotationType) != null;
    }


    /**
     * 从某个类上获取注解对象。注解可以深度递归。
     * <p>
     * 如果存在多个继承注解，则优先获取浅层第一个注解，如果浅层不存在，则返回第一个获取到的注解。
     * <p>
     * 请尽可能保证仅存在一个或者一种继承注解，否则获取到的类型将不可控。
     *
     * @param fromInstance   from的实例类，一般都是注解才需要。
     * @param from           获取注解的某个类
     * @param annotationType 想要获取的注解类型
     * @param ignored        获取注解列表的时候的忽略列表
     * @return 获取到的第一个注解对象
     */
    @SafeVarargs
    private static <T extends Annotation> T getAnnotation(Annotation fromInstance, AnnotatedElement from, Class<T> annotationType, Class<T>... ignored) {
        // 首先尝试获取缓存
        T cache = getCache(from, annotationType);
        if (cache != null) {
            return cache;
        }


        if (isNull(from, annotationType)) {
            return null;
        }

        // 可重复注解


        //先尝试直接获取
        T annotation = from.getAnnotation(annotationType);


        // 寻找 MixRepeatableAnnotations标记
        boolean mix;
        if (MixRepeatableAnnotations.class.equals(annotationType)) {
            mix = false;
        } else {
            mix = containsAnnotation(annotationType, MixRepeatableAnnotations.class);
        }
        Class<? extends Annotation> childrenValueAnnotateType;
        //如果存在直接返回，否则查询
        if (annotation != null) {
            // 首先将其转化为对应的代理实例
            annotation = AnnotationValueUtil.checkAnnotationProxy(annotation);

            if (mix) {
                // 如果需要混合，看看是否是可重复注解。
                childrenValueAnnotateType = repeatableChildType(annotationType);
                if (childrenValueAnnotateType != null) {
                    // repeatable
                    // 得到annotation的value值，里面是真正的数组
                    Object value = getAnnotationProperty(
                            annotation,
                            "value",
                            rt -> rt.isArray() && rt.getComponentType().equals(childrenValueAnnotateType)
                    );
                    if (value != null) {
                        Annotation[] childrenArray = (Annotation[]) value;
                        // 有value, 获取额外的annotation，合并然后返回
                        List<Annotation> annotationList = repeatChildrenFromAnnotationArray(from, childrenValueAnnotateType, from.getAnnotations());
                        annotationList.addAll(0, Arrays.asList(childrenArray));

                        // 查询完了，如果存在内容，构建参数
                        if (!annotationList.isEmpty()) {
                            final Object childrenValueAnnotateArray = Array.newInstance(childrenValueAnnotateType, annotationList.size());
                            for (int i = 0; i < annotationList.size(); i++) {
                                Array.set(childrenValueAnnotateArray, i, annotationList.get(i));
                            }
                            annotation = AnnotationValueUtil.setValue(annotation, "value", childrenValueAnnotateArray);
                        }

                    }

                }

            }
            // 如果不需要混合, 不管如何都直接返回

            return mappingAndSaveCache(fromInstance, from, annotation);
        }


        // 获取target注解
        Target target = annotationType.getAnnotation(Target.class);
        // 判断这个注解能否标注在其他注解上，如果不能，则不再深入获取
        boolean annotateAble = false;
        if (target != null) {
            for (ElementType elType : target.value()) {
                if (elType == ElementType.TYPE || elType == ElementType.ANNOTATION_TYPE) {
                    annotateAble = true;
                    break;
                }
            }
        }

        // 如果是一个可重复类型的注解, 并且无法直接获得, 则从当前的全部注解中寻找他的所有子类并构建一个代理。

        // 判断是否为某可重复注解的复数注解

        // 如果是可重复型, 此为其子类型
        // Class<? extends Annotation> childrenValueAnnotateType = null;

        childrenValueAnnotateType = repeatableChildType(annotationType);

        boolean repeatable = childrenValueAnnotateType != null;

        Annotation[] annotations = from.getAnnotations();

        if (!repeatable) {
            // 不是可重复注解的父类类型, 递归查询
            annotation = annotateAble ? getAnnotationFromArrays(fromInstance, annotations, annotationType, ignored) : null;
        } else {
            List<Annotation> annotationList = repeatChildrenFromAnnotationArray(from, childrenValueAnnotateType, annotations);

            // 查询完了，如果存在内容，构建参数
            if (!annotationList.isEmpty()) {
                final Object childrenValueAnnotateArray = Array.newInstance(childrenValueAnnotateType, annotationList.size());
                for (int i = 0; i < annotationList.size(); i++) {
                    Array.set(childrenValueAnnotateArray, i, annotationList.get(i));
                }
                // AnnotationValueUtil.setValue(annotation, "value", childrenValueAnnotateArray);

                Map<String, Object> map = new HashMap<>(1);
                map.put("value", childrenValueAnnotateArray);
                annotation = AnnotationProxyUtil.proxy(annotationType, map);
            }
        }


        // 如果最终不是null，计入缓存
        if (annotation != null) {
            annotation = mappingAndSaveCache(fromInstance, from, annotation);
        } else {
            nullCache(from, annotationType);
        }

        return annotation;
    }


    /**
     * 从数组中获取指定注解实例。
     * @param from 如果是来自与另一个注解的, 此处是来源。可以为null
     * @param array 注解数组
     * @param annotationType 注解类型
     */
    @SuppressWarnings("unchecked")
    @SafeVarargs
    private static <T extends Annotation> T getAnnotationFromArrays(Annotation from, Annotation[] array, Class<T> annotationType, Class<T>... ignored) {
        //先浅查询第一层
        //全部注解
        Annotation[] annotations = Arrays.stream(array)
                .filter(a -> {
                    for (Class<? extends Annotation> aType : ignored) {
                        if (a.annotationType().equals(aType)) {
                            return false;
                        }
                    }
                    return true;
                })
                .filter(a -> {
                    if (a == null) {
                        return false;
                    }
                    //如果此注解的类型就是我要的，直接放过
                    if (a.annotationType().equals(annotationType)) {
                        return true;
                    }
                    //否则，过滤掉java原生注解对象
                    //通过包路径判断
                    return !JAVA_ANNOTATION_PACKAGE.equals(a.annotationType().getPackage());
                }).peek(a -> {
                    if (from != null) {
                        mapping(from, a);
                    }
                }).toArray(Annotation[]::new);


        if (annotations.length == 0) {
            return null;
        }

        Class<? extends Annotation>[] annotationTypes = new Class[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            annotationTypes[i] = annotations[i].annotationType();
        }

        Class<T>[] newIgnored = new Class[annotationTypes.length + ignored.length];
        System.arraycopy(ignored, 0, newIgnored, 0, ignored.length);
        System.arraycopy(annotationTypes, 0, newIgnored, ignored.length, annotationTypes.length);


        //如果浅层查询还是没有，递归查询

        for (Annotation a : annotations) {
            T annotationGet = getAnnotation(a, a.annotationType(), annotationType, newIgnored);
            if (annotationGet != null) {
                return annotationGet;
            }
        }

        //如果还是没有找到，返回null
        return null;
    }

    /**
     * 从缓存中获取缓存注解
     *
     * @param from          来源
     * @param annotatedType 注解类型
     * @return 注解缓存，可能为null
     */
    @SuppressWarnings("unchecked")
    private static <T extends Annotation> T getCache(AnnotatedElement from, Class<T> annotatedType) {
        Map<Class<? extends Annotation>, Annotation> cacheMap = ANNOTATION_CACHE.get(from);
        if (cacheMap != null) {
            return (T) cacheMap.get(annotatedType);
        }
        // 找不到，返回null
        return null;
    }

    /**
     * 记录一个得不到的缓存
     *
     * @param from          {@link AnnotatedElement}
     * @param annotatedType annotation class
     */
    private static <T extends Annotation> void nullCache(AnnotatedElement from, Class<T> annotatedType) {
        final Set<Class<? extends Annotation>> classes = NULL_CACHE.computeIfAbsent(from, k -> new LinkedHashSet<>());
        classes.add(annotatedType);
    }

    /**
     * 判断是否获取不到
     *
     * @param from          {@link AnnotatedElement}
     * @param annotatedType annotation class
     */
    private static <T extends Annotation> boolean isNull(AnnotatedElement from, Class<T> annotatedType) {
        final Set<Class<? extends Annotation>> classes = NULL_CACHE.get(from);
        if (classes == null || classes.isEmpty()) {
            return false;
        }
        return classes.contains(annotatedType);
    }


    /**
     * 记录一条缓存记录。
     */
    private static void saveCache(AnnotatedElement from, Annotation annotation) {
        Map<Class<? extends Annotation>, Annotation> cacheMap;
        synchronized (ANNOTATION_CACHE) {
            // 如果为空，新建一个并保存
            cacheMap = ANNOTATION_CACHE.computeIfAbsent(from, k -> new LinkedHashMap<>());
            // 记录这个注解
            cacheMap.put(annotation.annotationType(), annotation);
        }
    }


    /**
     * 执行注解映射
     */
    private static <T extends Annotation> T mapping(Annotation from, T to) {
        final Class<? extends Annotation> fromAnnotationType = from.annotationType();
        final Method[] methods = fromAnnotationType.getMethods();
        final Map<String, Object> params = new HashMap<>();
        final AnnotateMapping classAnnotateMapping = fromAnnotationType.getAnnotation(AnnotateMapping.class);
        AnnotateMapping annotateMapping;
        //noinspection unchecked
        final Class<T> toType = (Class<T>) to.annotationType();
        for (Method method : methods) {
            if (OBJECT_METHODS.contains(method.getName())) {
                continue;
            }
            annotateMapping = method.getAnnotation(AnnotateMapping.class);
            if (annotateMapping == null) {
                annotateMapping = classAnnotateMapping;
            }
            if (annotateMapping != null) {
                if (annotateMapping.value().equals(toType)) {
                    String name = annotateMapping.name();
                    if (name.length() == 0) {
                        name = method.getName();
                    }
                    try {
                        // if (!method.canAccess()) {
                        method.setAccessible(true);
                        // }

                        Object value = method.invoke(from);
                        params.put(name, value);
                    } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("cannot map " + name + " for " + method, e);
                    }
                }
            }
        }
        return AnnotationProxyUtil.proxy(toType, to, params);
    }


    /**
     * 获取任意注解的任意参数，如果有的话。
     *
     * @param annotation      注解
     * @param propertyName    参数名
     * @param returnTypeCheck 验证其返回值类型
     * @return real properties value or null.
     */
    public static Object getAnnotationProperty(Annotation annotation, String propertyName, Predicate<Class<?>> returnTypeCheck) {
        Objects.requireNonNull(propertyName, "Parameter propertyName cannot be null.");

        if (annotation instanceof AnnotationInvocationHandler) {
            return ((AnnotationInvocationHandler) annotation).get(propertyName);
        }

        try {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            Method[] methods = annotationType.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(propertyName)) {
                    if (returnTypeCheck.test(method.getReturnType())) {
                        return method.invoke(annotation);
                    } else {
                        return null;
                    }
                }
            }
        } catch (InvocationTargetException | IllegalAccessException ignore) {
        }
        return null;

    }


    /**
     * 进行注解值映射，并缓存，返回
     */
    private static <T extends Annotation> T mappingAndSaveCache(Annotation fromInstance, AnnotatedElement from, T annotation) {
        // 如果from是一个注解, 则构建一个新的annotation实例
        if (fromInstance != null && from instanceof Class && ((Class<?>) from).isAnnotation()) {
            return mapping(fromInstance, annotation);
        } else {
            saveCache(from, annotation);
            return annotation;
        }
    }


    /**
     * 清除缓存
     */
    public static void cleanCache() {
        ANNOTATION_CACHE.clear();
        NULL_CACHE.clear();
    }


    /**
     * 重置annotation lru map的最大值。
     *
     */
    public static void setAnnotationCacheCapacity(int capacity) {
        ANNOTATION_CACHE.setCapacity(capacity);
    }

    /**
     * 重置null annotation lru map的最大值。
     *
     */
    public static void setNullAnnotationCacheCapacity(int capacity) {
        NULL_CACHE.setCapacity(capacity);
    }


    /**
     * 得到可重复注解的子类型。 如果为非可重复类型，得到null，
     *
     * @param annotationType 疑似可重复注解的类型。
     * @param <T>            Annotation
     * @return childAnnotationType
     */
    private static <T extends Annotation> Class<? extends Annotation> repeatableChildType(Class<T> annotationType) {
        // 判断是否为某可重复注解的复数注解
        // 如果是可重复型, 此为其子类型
        Class<? extends Annotation> childValueAnnotateType = null;
        try {
            Method valueMethod = annotationType.getMethod("value");
            final Class<?> valueMethodReturnType = valueMethod.getReturnType();
            if (valueMethodReturnType.isArray()) {
                final Class<?> valueArrayType = valueMethodReturnType.getComponentType();
                if (valueArrayType.isAnnotation()) {
                    Class<? extends Annotation> valueArrayTypeForAnnotation = (Class<? extends Annotation>) valueArrayType;
                    // is annotation
                    Repeatable repeatableAnnotate = valueArrayTypeForAnnotation.getAnnotation(Repeatable.class);
                    // 如果value的返回值
                    if (repeatableAnnotate != null && repeatableAnnotate.value().equals(annotationType)) {
                        childValueAnnotateType = valueArrayTypeForAnnotation;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        }

        return childValueAnnotateType;
    }


    private static <T extends Annotation> List<Annotation> repeatChildrenFromAnnotationArray(AnnotatedElement from, Class<T> childrenValueAnnotateType, Annotation[] annotations) {
        List<Annotation> annotationList = new ArrayList<>();

        // 先尝试直接获取
        Annotation getForm = from.getAnnotation(childrenValueAnnotateType);
        if (getForm != null) {
            annotationList.add(getForm);
        }

        // 是可重复注解的父类类型, 得到他对应的子类注解类型.
        for (Annotation annotate : annotations) {
            final Annotation getAnnotation = getAnnotation(annotate, annotate.annotationType(), childrenValueAnnotateType);
            if (getAnnotation != null) {
                annotationList.add(getAnnotation);
            }
        }

        return annotationList;
    }

}
