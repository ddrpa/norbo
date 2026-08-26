package cc.ddrpa.dorian.norbo.mabtisplus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 生成基于 MyBatis-Plus {@code IService}/{@code ServiceImpl} 的 {@code {Entity}Repository} 类。
 * <p>
 * 用于仍使用经典 IService 风格（而非 3.5.9+ 的 repository 抽象）的项目。
 *
 * @see MPRepository
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MPService {

    String value() default "";

    /**
     * 显式指定生成类的包名，也可通过 value 指定
     * <p>
     * 默认与被修饰类位于同一个包下
     */
    String packageName() default "";
}
