package cc.ddrpa.dorian.norbo.mabtisplus.processor;

import static javax.lang.model.element.Modifier.PUBLIC;

import cc.ddrpa.dorian.norbo.mabtisplus.annotation.MPTypeHandler;
import cc.ddrpa.dorian.norbo.util.AnnotationUtils;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("cc.ddrpa.dorian.norbo.mabtisplus.annotation.MPTypeHandler")
public class MPTypeHandlerProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(MPTypeHandler.class)) {
            if (!annotatedElement.getKind().isClass() && !annotatedElement.getKind().isField()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    String.format("Only class or field can be annotated with @%s",
                        MPTypeHandler.class.getSimpleName()),
                    annotatedElement);
            }
            ClassName className = ClassName.get(
                packageName(annotatedElement),
                buildNameFromType(annotatedElement.asType()) + "TypeHandler");

            if (Objects.nonNull(elementUtils.getTypeElement(className.canonicalName()))) {
                // 在类定义和属性上为同一个类型添加注解时，发现类已经存在就不需要再生成
                return false;
            }

            // 修饰对象的类型
            TypeName annotatedElementType = TypeName.get(annotatedElement.asType());
            // 判断修饰对象是否为参数化类型
            boolean isElementParameterized = annotatedElement.asType() instanceof DeclaredType d &&
                !d.getTypeArguments().isEmpty();

            TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(className);
            // @MappedTypes
            if (isElementParameterized) {
                DeclaredType declaredType = (DeclaredType) annotatedElement.asType();
                TypeElement rawType = (TypeElement) declaredType.asElement();
                typeBuilder.addAnnotation(
                    AnnotationSpec.builder(
                            ClassName.get("org.apache.ibatis.type", "MappedTypes"))
                        .addMember("value", "$T.class", ClassName.get(rawType))
                        .build());
            } else {
                typeBuilder.addAnnotation(
                    AnnotationSpec.builder(
                            ClassName.get("org.apache.ibatis.type", "MappedTypes"))
                        .addMember("value", "$T.class", annotatedElementType)
                        .build());
            }

            typeBuilder.addAnnotation(
                    AnnotationSpec.builder(ClassName.get("org.apache.ibatis.type", "MappedJdbcTypes"))
                        .addMember("value",
                            "{$T.$L, $T.$L}",
                            ClassName.get("org.apache.ibatis.type", "JdbcType"), "VARCHAR",
                            ClassName.get("org.apache.ibatis.type", "JdbcType"), "LONGVARCHAR")
                        .build())
                .addModifiers(PUBLIC)
                .superclass(
                    ParameterizedTypeName.get(
                        ClassName.get("com.baomidou.mybatisplus.extension.handlers",
                            "AbstractJsonTypeHandler"),
                        annotatedElementType));

            // NEED_CHECK 应当为每个 typeHandler 创建自己的 objectMapper 实例还是实现依赖注入？
            // 添加 ObjectMapper
            typeBuilder.addField(
                FieldSpec.builder(ClassName.get("com.fasterxml.jackson.databind", "ObjectMapper"),
                        "mapper")
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T()",
                        ClassName.get("com.fasterxml.jackson.databind", "ObjectMapper"))
                    .build());

            // 添加方法到类
            typeBuilder
                .addMethod(constructorMethod())
                .addMethod(parseMethod(annotatedElementType, isElementParameterized))
                .addMethod(toJsonMethod(annotatedElementType));

            JavaFile file = JavaFile.builder(className.packageName(), typeBuilder.build()).build();
            try {
                file.writeTo(filer);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write file for element",
                    annotatedElement);
            }
        }
        return false;
    }

    /**
     * 推断生成类的 packageName
     *
     * @param annotatedElement
     * @return
     */
    protected String packageName(Element annotatedElement) {
        Optional<AnnotationMirror> mirrorOpt = AnnotationUtils.getAnnotationMirror(
            annotatedElement, MPTypeHandler.class.getCanonicalName());
        Optional<String> optionalPackageName = mirrorOpt
            .flatMap(mirror -> AnnotationUtils.getAnnotationValue(mirror, "packageName"))
            .flatMap(v -> {
                Object rawValue = v.getValue();
                if (rawValue instanceof String s) {
                    return Optional.of(s);
                } else {
                    return Optional.empty();
                }
            });
        if (optionalPackageName.isPresent() && !optionalPackageName.get().isBlank()) {
            return optionalPackageName.get();
        }
        Optional<String> optionalValue = mirrorOpt
            .flatMap(mirror -> AnnotationUtils.getAnnotationValue(mirror, "value"))
            .flatMap(v -> {
                Object rawValue = v.getValue();
                if (rawValue instanceof String s) {
                    return Optional.of(s);
                } else {
                    return Optional.empty();
                }
            });
        if (optionalValue.isPresent() && !optionalValue.get().isBlank()) {
            return optionalValue.get();
        }
        return elementUtils.getPackageOf(annotatedElement).getQualifiedName()
            .toString();
    }

    protected String buildNameFromType(TypeMirror typeMirror) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return typeMirror.toString();
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        TypeElement rawType = (TypeElement) declaredType.asElement();
        String rawName = rawType.getSimpleName().toString();
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (typeArgs.isEmpty()) {
            return rawName;
        }
        StringBuilder nameBuilder = new StringBuilder(rawName);
        nameBuilder.append("Of");
        List<String> argNames = new ArrayList<>();
        for (TypeMirror arg : typeArgs) {
            argNames.add(buildNameFromType(arg));
        }
        nameBuilder.append(String.join("And", argNames));
        return nameBuilder.toString();
    }

    /**
     * 创建构造函数，在构造函数中配置 ObjectMapper
     *
     * @return
     */
    protected MethodSpec constructorMethod() {
        return MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(Class.class, "type")
            .addStatement("super(type)")
            // ObjectMapper 配置
            .addStatement("mapper.registerModule(new $T())",
                ClassName.get("com.fasterxml.jackson.datatype.jsr310", "JavaTimeModule"))
            .addStatement("mapper.disable($T.$L)",
                ClassName.get("com.fasterxml.jackson.databind", "SerializationFeature"),
                "WRITE_DATES_AS_TIMESTAMPS")
            .addStatement("mapper.registerModule(new $T())",
                ClassName.get("com.fasterxml.jackson.datatype.jdk8", "Jdk8Module"))
            .addStatement("mapper.disable($T.$L)",
                ClassName.get("com.fasterxml.jackson.databind", "SerializationFeature"),
                "FAIL_ON_EMPTY_BEANS")
            .addStatement("mapper.disable($T.$L)",
                ClassName.get("com.fasterxml.jackson.databind", "DeserializationFeature"),
                "FAIL_ON_UNKNOWN_PROPERTIES")
            .addStatement("mapper.disable($T.$L)",
                ClassName.get("com.fasterxml.jackson.databind", "DeserializationFeature"),
                "FAIL_ON_NULL_FOR_PRIMITIVES")
            .addStatement("mapper.enable($T.$L)",
                ClassName.get("com.fasterxml.jackson.databind", "DeserializationFeature"),
                "ACCEPT_EMPTY_STRING_AS_NULL_OBJECT")
            .addStatement("mapper.disable($T.$L)",
                ClassName.get("com.fasterxml.jackson.databind", "DeserializationFeature"),
                "FAIL_ON_NUMBERS_FOR_ENUMS")
            .addStatement(
                "mapper.configOverride($T.class).setSetterInfo($T.forContentNulls($T.AS_EMPTY))",
                List.class,
                ClassName.get("com.fasterxml.jackson.annotation", "JsonSetter", "Value"),
                ClassName.get("com.fasterxml.jackson.annotation", "Nulls"))
            .addStatement(
                "mapper.configOverride($T.class).setSetterInfo($T.forValueNulls($T.AS_EMPTY))",
                List.class,
                ClassName.get("com.fasterxml.jackson.annotation", "JsonSetter", "Value"),
                ClassName.get("com.fasterxml.jackson.annotation", "Nulls"))
            .build();
    }

    protected MethodSpec parseMethod(TypeName annotatedElementType,
        boolean isElementParameterized) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("parse")
            .addAnnotation(ClassName.get("lombok", "SneakyThrows"))
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "json")
            .returns(annotatedElementType);
        if (isElementParameterized) {
            methodBuilder.addStatement("return mapper.readValue(json, new $T<$T>() {})",
                ClassName.get("com.fasterxml.jackson.core.type", "TypeReference"),
                annotatedElementType);
        } else {
            methodBuilder.addStatement("return mapper.readValue(json, $T.class)",
                annotatedElementType);
        }
        return methodBuilder.build();
    }

    protected MethodSpec toJsonMethod(TypeName annotatedElementType) {
        // 构建 toJson 方法
        return MethodSpec.methodBuilder("toJson")
            .addAnnotation(ClassName.get("lombok", "SneakyThrows"))
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(annotatedElementType, "obj")
            .addStatement("return mapper.writeValueAsString(obj)")
            .returns(String.class)
            .build();
    }
}
