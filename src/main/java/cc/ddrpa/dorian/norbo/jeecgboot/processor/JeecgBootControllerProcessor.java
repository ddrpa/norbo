package cc.ddrpa.dorian.norbo.jeecgboot.processor;

import cc.ddrpa.dorian.norbo.jeecgboot.annotation.JeecgBootController;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("cc.ddrpa.dorian.norbo.jeecgboot.annotation.JeecgBootController")
public class JeecgBootControllerProcessor extends AbstractProcessor {

    private static final ClassName JEECG_RESULT_CLASS_NAME = ClassName.get(
        "org.jeecg.common.api.vo", "Result");
    private static final ClassName JEECG_AUTOLOG_CLASS_NAME = ClassName.get(
        "org.jeecg.common.aspect.annotation", "AutoLog");
    private static final ClassName SWAGGER_TAG_CLASS_NAME = ClassName.get(
        "io.swagger.v3.oas.annotations.tags", "Tag");
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
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(
            JeecgBootController.class)) {
            if (!annotatedElement.getKind().isClass()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    String.format("Only class can be annotated with @%s",
                        JeecgBootController.class.getSimpleName()),
                    annotatedElement);
            }
            // 获取注解中的属性
            String moduleDescription = annotatedElement.getAnnotation(JeecgBootController.class)
                .value();

            // 生成包名
            String packageName = elementUtils.getPackageOf(annotatedElement).getQualifiedName()
                .toString();
            // 生成类名
            String simpleClassName = String.format("%sController",
                annotatedElement.getSimpleName());
            ClassName controllerClassName = ClassName.get(packageName, simpleClassName);

            // 获取实体类类型
            ClassName entityClassName = ClassName.get(packageName,
                String.valueOf(annotatedElement.getSimpleName()));

            // 推断存储类
            String repositoryClassNameAsString = String.format("%sRepository",
                annotatedElement.getSimpleName());
            ClassName repositoryClassName = ClassName.get(packageName, repositoryClassNameAsString);
            // 推断存储类的 bean name
            // 一般为首字母小写
            String repositoryBeanName;
            // 如果连续 N 个字母为大写，则前 N-1 个字母小写
            int charCount = 0;
            while (charCount < repositoryClassNameAsString.length()
                && Character.isUpperCase(repositoryClassNameAsString.charAt(charCount))) {
                charCount++;
            }
            if (charCount == 1) {
                // 第二个字母为小写，按常见驼峰处理
                repositoryBeanName = repositoryClassNameAsString.substring(0, 1).toLowerCase()
                    + repositoryClassNameAsString.substring(1);
            } else {
                // 第 N 个字母为小写，前 N-1 个字母小写
                repositoryBeanName =
                    repositoryClassNameAsString.substring(0, charCount - 1).toLowerCase()
                        + repositoryClassNameAsString.substring(charCount - 1);
            }

            // 创建类声明
            TypeSpec typeSpec = TypeSpec.classBuilder(controllerClassName)
                .addAnnotation(AnnotationSpec.builder(SWAGGER_TAG_CLASS_NAME)
                    .addMember("name", "$S", moduleDescription)
                    .build())
                .addAnnotation(
                    ClassName.get("org.springframework.web.bind.annotation", "RestController"))
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
                    .addMember("value", "$S", "")
                    .build())
                .addAnnotation(ClassName.get("lombok", "RequiredArgsConstructor"))
                .addModifiers(Modifier.PUBLIC)
                .superclass(ParameterizedTypeName.get(
                    ClassName.get("org.jeecg.common.system.base.controller", "JeecgController"),
                    ClassName.get(annotatedElement.asType()),
                    repositoryClassName))
                .addField(FieldSpec.builder(ClassName.get("org.slf4j", "Logger"),
                        "logger",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.getLogger($L.class)",
                        ClassName.get("org.slf4j", "LoggerFactory"),
                        annotatedElement.getSimpleName())
                    .build())
                .addField(FieldSpec.builder(repositoryClassName, repositoryBeanName,
                        Modifier.PRIVATE, Modifier.FINAL)
                    .build())
                .addMethod(
                    queryPageListMethod(entityClassName, repositoryBeanName, moduleDescription))
                .addMethod(add(entityClassName, repositoryBeanName, moduleDescription))
                .addMethod(edit(entityClassName, repositoryBeanName, moduleDescription))
                .addMethod(delete(entityClassName, repositoryBeanName, moduleDescription))
                .addMethod(deleteBatch(entityClassName, repositoryBeanName, moduleDescription))
                .addMethod(queryById(entityClassName, repositoryBeanName, moduleDescription))
                .addMethod(exportExcel(entityClassName))
                .addMethod(importExcel(entityClassName))
                .build();

            JavaFile file = JavaFile.builder(controllerClassName.packageName(), typeSpec).build();
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
     * 分页列表查询
     *
     * @param entityClassName
     * @param repositoryBeanName
     * @param moduleDescription
     * @return
     */
    protected MethodSpec queryPageListMethod(ClassName entityClassName, String repositoryBeanName,
        String moduleDescription) {
        return MethodSpec.methodBuilder("queryPageList")
            .addJavadoc("分页列表查询\n\n" +
                "@param plan\n" +
                "@param pageNo\n" +
                "@param pageSize\n" +
                "@param req\n" +
                "@return\n")
            .addAnnotation(AnnotationSpec.builder(JEECG_AUTOLOG_CLASS_NAME)
                .addMember("value", "$S", String.format("%s-分页列表查询", moduleDescription))
                .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("io.swagger.v3.oas.annotations", "Operation"))
                .addMember("summary", "$S", String.format("%s-分页列表查询", moduleDescription))
                .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                .addMember("value", "$S", "/list")
                .build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(JEECG_RESULT_CLASS_NAME,
                ParameterizedTypeName.get(
                    ClassName.get("com.baomidou.mybatisplus.core.metadata", "IPage"),
                    entityClassName)))
            .addParameter(entityClassName, "entity")
            .addParameter(ParameterSpec.builder(ClassName.get("java.lang", "Integer"),
                    "pageNo")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                    .addMember("name", "$S", "pageNo")
                    .addMember("defaultValue", "$S", "1")
                    .build())
                .build())
            .addParameter(ParameterSpec.builder(ClassName.get("java.lang", "Integer"),
                    "pageSize")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                    .addMember("name", "$S", "pageSize")
                    .addMember("defaultValue", "$S", "10")
                    .build())
                .build())
            .addParameter(ClassName.get("jakarta.servlet.http", "HttpServletRequest"),
                "request")
            .addCode(CodeBlock.builder()
                .addStatement(
                    "$T<$T> queryWrapper = $T.initQueryWrapper(entity, request.getParameterMap())",
                    ClassName.get("com.baomidou.mybatisplus.core.conditions.query", "QueryWrapper"),
                    entityClassName,
                    ClassName.get("org.jeecg.common.system.query", "QueryGenerator"))
                .addStatement("$T<$T> page = new $T<>(pageNo, pageSize)",
                    ClassName.get("com.baomidou.mybatisplus.extension.plugins.pagination", "Page"),
                    entityClassName,
                    ClassName.get("com.baomidou.mybatisplus.extension.plugins.pagination", "Page"))
                .addStatement("$T<$T> pageList = $L.page(page, queryWrapper)",
                    ClassName.get("com.baomidou.mybatisplus.core.metadata", "IPage"),
                    entityClassName,
                    repositoryBeanName)
                .addStatement("return $T.OK(pageList)", JEECG_RESULT_CLASS_NAME)
                .build())
            .build();
    }


    /**
     * 添加
     *
     * @param entityClassName
     * @param repositoryBeanName
     * @param moduleDescription
     * @return
     */
    protected MethodSpec add(ClassName entityClassName, String repositoryBeanName,
        String moduleDescription) {
        return MethodSpec.methodBuilder("add")
            .addJavadoc("添加\n\n" +
                "@param plan\n" +
                "@return\n")
            .addAnnotation(AnnotationSpec.builder(JEECG_AUTOLOG_CLASS_NAME)
                .addMember("value", "$S", String.format("%s-添加", moduleDescription)).build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Operation"))
                    .addMember("summary", "$S", String.format("%s-添加", moduleDescription))
                    .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                .addMember("value", "$S", "/add").build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                JEECG_RESULT_CLASS_NAME, ClassName.get("java.lang", "String")))
            .addParameter(ParameterSpec.builder(entityClassName, "entity")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestBody"))
                    .build())
                .build())
            .addStatement("$L.save(entity)", repositoryBeanName)
            .addStatement("return $T.OK($S)", JEECG_RESULT_CLASS_NAME, "添加成功！")
            .build();
    }

    /**
     * 编辑
     *
     * @param entityClassName
     * @param repositoryBeanName
     * @param moduleDescription
     * @return
     */
    protected MethodSpec edit(ClassName entityClassName, String repositoryBeanName,
        String moduleDescription) {
        return MethodSpec.methodBuilder("edit")
            .addJavadoc("编辑\n\n" +
                "@param plan\n" +
                "@return\n")
            .addAnnotation(AnnotationSpec.builder(JEECG_AUTOLOG_CLASS_NAME)
                .addMember("value", "$S", String.format("%s-编辑", moduleDescription)).build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Operation"))
                    .addMember("summary", "$S", String.format("%s-编辑", moduleDescription))
                    .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
                .addMember("value", "$S", "/edit")
                .addMember("method", "{$T.PUT, $T.POST}",
                    ClassName.get("org.springframework.web.bind.annotation", "RequestMethod"),
                    ClassName.get("org.springframework.web.bind.annotation", "RequestMethod"))
                .build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                JEECG_RESULT_CLASS_NAME, ClassName.get("java.lang", "String")))
            .addParameter(ParameterSpec.builder(entityClassName, "entity")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestBody"))
                    .build())
                .build())
            .addStatement("$L.updateById(entity)", repositoryBeanName)
            .addStatement("return $T.OK($S)", JEECG_RESULT_CLASS_NAME, "编辑成功!")
            .build();
    }

    /**
     * 通过id删除
     *
     * @param entityClassName
     * @param repositoryBeanName
     * @param moduleDescription
     * @return
     */
    protected MethodSpec delete(ClassName entityClassName, String repositoryBeanName,
        String moduleDescription) {
        return MethodSpec.methodBuilder("delete")
            .addJavadoc("通过id删除\n\n" +
                "@param id\n" +
                "@return\n")
            .addAnnotation(AnnotationSpec.builder(JEECG_AUTOLOG_CLASS_NAME)
                .addMember("value", "$S", String.format("%s-通过id删除", moduleDescription))
                .build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Operation"))
                    .addMember("summary", "$S", String.format("%s-通过id删除", moduleDescription))
                    .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "DeleteMapping"))
                .addMember("value", "$S", "/delete").build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                JEECG_RESULT_CLASS_NAME, ClassName.get("java.lang", "String")))
            .addParameter(ParameterSpec.builder(ClassName.get("java.lang", "String"), "id")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                    .addMember("name", "$S", "id")
                    .addMember("required", "$L", "true")
                    .build())
                .build())
            .addStatement("$L.removeById(id)", repositoryBeanName)
            .addStatement("return $T.OK($S)", JEECG_RESULT_CLASS_NAME, "删除成功!")
            .build();
    }

    /**
     * 批量删除
     *
     * @param entityClassName
     * @param repositoryBeanName
     * @param moduleDescription
     * @return
     */
    protected MethodSpec deleteBatch(ClassName entityClassName, String repositoryBeanName,
        String moduleDescription) {
        return MethodSpec.methodBuilder("deleteBatch")
            .addJavadoc("批量删除\n\n" +
                "@param ids\n" +
                "@return\n")
            .addAnnotation(AnnotationSpec.builder(JEECG_AUTOLOG_CLASS_NAME)
                .addMember("value", "$S", String.format("%s-批量删除", moduleDescription)).build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Operation"))
                    .addMember("summary", "$S", String.format("%s-批量删除", moduleDescription))
                    .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "DeleteMapping"))
                .addMember("value", "$S", "/deleteBatch").build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                JEECG_RESULT_CLASS_NAME, ClassName.get("java.lang", "String")))
            .addParameter(ParameterSpec.builder(ClassName.get("java.lang", "String"), "ids")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                    .addMember("name", "$S", "ids")
                    .addMember("required", "$L", "true")
                    .build())
                .build())
            .addStatement("$L.removeByIds($T.asList(ids.split($S)))", repositoryBeanName,
                ClassName.get("java.util", "Arrays"), ",")
            .addStatement("return $T.OK($S)", JEECG_RESULT_CLASS_NAME, "批量删除成功!")
            .build();
    }

    /**
     * 通过id查询
     *
     * @param entityClassName
     * @param repositoryBeanName
     * @param moduleDescription
     * @return
     */
    protected MethodSpec queryById(ClassName entityClassName, String repositoryBeanName,
        String moduleDescription) {
        return MethodSpec.methodBuilder("queryById")
            .addJavadoc("通过id查询\n\n" +
                "@param id\n" +
                "@return\n")
            .addAnnotation(AnnotationSpec.builder(JEECG_AUTOLOG_CLASS_NAME)
                .addMember("value", "$S", String.format("%s-通过id查询", moduleDescription))
                .build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("io.swagger.v3.oas.annotations", "Operation"))
                    .addMember("summary", "$S", String.format("%s-通过id查询", moduleDescription))
                    .build())
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                .addMember("value", "$S", "/queryById").build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(JEECG_RESULT_CLASS_NAME, entityClassName))
            .addParameter(ParameterSpec.builder(ClassName.get("java.lang", "String"), "id")
                .addAnnotation(AnnotationSpec.builder(
                        ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                    .addMember("name", "$S", "id")
                    .addMember("required", "$L", "true")
                    .build())
                .build())
            .addStatement("$T entity = $L.getById(id)", entityClassName, repositoryBeanName)
            .addStatement("return $T.OK(entity)", JEECG_RESULT_CLASS_NAME)
            .build();
    }

    /**
     * 导出excel
     *
     * @param entityClassName
     * @return
     */
    protected MethodSpec exportExcel(ClassName entityClassName) {
        return MethodSpec.methodBuilder("exportExcel")
            .addJavadoc("导出excel\n\n" +
                "@param request\n" +
                "@param plan\n")
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
                .addMember("value", "$S", "/exportXls")
                .build())
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("org.springframework.web.servlet", "ModelAndView"))
            .addParameter(ClassName.get("jakarta.servlet.http", "HttpServletRequest"), "request")
            .addParameter(entityClassName, "entity")
            .addStatement("return super.exportXls(request, entity, $T.class, $S)",
                entityClassName, "ok")
            .build();
    }

    /**
     * 通过excel导入数据
     *
     * @param entityClassName
     * @return
     */
    protected MethodSpec importExcel(ClassName entityClassName) {
        return MethodSpec.methodBuilder("importExcel")
            .addJavadoc("通过excel导入数据\n\n" +
                "@param request\n" +
                "@param plan\n")
            .addAnnotation(AnnotationSpec.builder(
                    ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                .addMember("value", "$S", "/importExcel")
                .build())
            .addModifiers(Modifier.PUBLIC)
            .returns(
                ParameterizedTypeName.get(JEECG_RESULT_CLASS_NAME,
                    WildcardTypeName.subtypeOf(Object.class)))
            .addParameter(ClassName.get("jakarta.servlet.http", "HttpServletRequest"), "request")
            .addParameter(ClassName.get("jakarta.servlet.http", "HttpServletResponse"), "response")
            .addStatement("return super.importExcel(request, response, $T.class)",
                entityClassName)
            .build();
    }
}