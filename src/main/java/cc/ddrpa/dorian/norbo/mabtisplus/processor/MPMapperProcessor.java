package cc.ddrpa.dorian.norbo.mabtisplus.processor;

import static javax.lang.model.element.Modifier.PUBLIC;

import cc.ddrpa.dorian.norbo.mabtisplus.annotation.MPMapper;
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

@SupportedAnnotationTypes("cc.ddrpa.dorian.norbo.mabtisplus.annotation.MPMapper")
public class MPMapperProcessor extends AbstractProcessor {

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
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(MPMapper.class)) {
            if (!annotatedElement.getKind().isClass()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    String.format("Only class can be annotated with @%s",
                        MPMapper.class.getSimpleName()),
                    annotatedElement);
            }

            ClassName classType = ClassName.get(packageName(annotatedElement),
                String.format("%sMapper", annotatedElement.getSimpleName()));

            TypeSpec typeSpec = TypeSpec.interfaceBuilder(classType)
                .addAnnotation(ClassName.get("org.apache.ibatis.annotations", "Mapper"))
                .addModifiers(PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(
                    ClassName.get("com.baomidou.mybatisplus.core.mapper", "BaseMapper"),
                    ClassName.get(annotatedElement.asType())))
                .build();
            JavaFile file = JavaFile.builder(classType.packageName(), typeSpec).build();
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
            annotatedElement, MPMapper.class.getCanonicalName());
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