package cc.ddrpa.dorian.norbo.mabtisplus.processor;

import static javax.lang.model.element.Modifier.PUBLIC;

import cc.ddrpa.dorian.norbo.mabtisplus.annotation.GenerateRepository;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("cc.ddrpa.dorian.norbo.mabtisplus.annotation.GenerateRepository")
public class GenerateRepositoryProcessor extends AbstractProcessor {

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
            GenerateRepository.class)) {
            if (!annotatedElement.getKind().isClass()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    String.format("Only class can be annotated with @%s",
                        GenerateRepository.class.getSimpleName()),
                    annotatedElement);
            }
            String simpleClassName = String.format("%sRepository",
                annotatedElement.getSimpleName());
            String packageName = elementUtils.getPackageOf(annotatedElement).getQualifiedName()
                .toString();
            ClassName classType = ClassName.get(packageName, simpleClassName);
            TypeSpec typeSpec = TypeSpec.classBuilder(classType)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Service"))
                .addModifiers(PUBLIC)
                .superclass(ParameterizedTypeName.get(
                    ClassName.get("com.baomidou.mybatisplus.extension.service.impl", "ServiceImpl"),
                    ClassName.get(packageName,
                        String.format("%sMapper", annotatedElement.getSimpleName())),
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
}