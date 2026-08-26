package cc.ddrpa.dorian.norbo.mabtisplus.processor;

import static javax.lang.model.element.Modifier.PUBLIC;

import cc.ddrpa.dorian.norbo.mabtisplus.annotation.MPService;
import cc.ddrpa.dorian.norbo.util.AnnotationUtils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("cc.ddrpa.dorian.norbo.mabtisplus.annotation.MPService")
public class MPServiceProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    public MPServiceProcessor() {
        super();
    }

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
            MPService.class)) {
            if (!annotatedElement.getKind().isClass()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    String.format("Only class can be annotated with @%s",
                        MPService.class.getSimpleName()),
                    annotatedElement);
            }

            String simpleClassName = String.format("%sRepository",
                annotatedElement.getSimpleName());
            String packageName = packageName(annotatedElement);
            ClassName classType = ClassName.get(packageName, simpleClassName);

            TypeSpec typeSpec = TypeSpec.classBuilder(classType)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Service"))
                .addModifiers(PUBLIC)
                .superclass(ParameterizedTypeName.get(
                    ClassName.get("com.baomidou.mybatisplus.spring.service.impl", "ServiceImpl"),
                    ClassName.get(packageName,
                        String.format("%sMapper", annotatedElement.getSimpleName())),
                    ClassName.get(annotatedElement.asType())))
                .build();
            JavaFile file = JavaFile.builder(classType.packageName(), typeSpec).build();
            try {
                file.writeTo(filer);
            } catch (IOException e) {
                if (isFileAlreadyExists(e)) {
                    // 文件已存在（例如 IDEA 增量编译未清理输出目录时重新生成），按 NOTE 上报并跳过
                    messager.printMessage(Diagnostic.Kind.NOTE,
                        "Skipped already-generated file for element " + annotatedElement,
                        annotatedElement);
                } else {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to write file for element",
                        annotatedElement);
                }
            }
        }
        return false;
    }

    /**
     * 判断 IOException 是否由"目标文件/源文件已存在"引起。
     * 只有真正的 IO 错误才需要以 ERROR 上报；文件已存在是增量编译时的正常情况。
     */
    private static boolean isFileAlreadyExists(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getName();
            if (name.endsWith("FileAlreadyExistsException")
                || name.endsWith("FilerException")
                || name.contains("FileAlreadyExists")) {
                return true;
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
            annotatedElement, MPService.class.getCanonicalName());
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
}
