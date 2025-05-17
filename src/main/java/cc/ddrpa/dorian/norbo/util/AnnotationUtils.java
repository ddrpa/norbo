package cc.ddrpa.dorian.norbo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public class AnnotationUtils {

    public static Optional<AnnotationMirror> getAnnotationMirror(Element element,
        String annotationClassName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(annotationClassName)) {
                return Optional.of(mirror);
            }
        }
        return Optional.empty();
    }

    public static Optional<AnnotationValue> getAnnotationValue(AnnotationMirror mirror,
        String propertyName) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues()
            .entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(propertyName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public static Optional<TypeMirror> getClassTypeMirror(AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return Optional.empty();
        }
        if (annotationValue.getValue() instanceof TypeMirror t) {
            return Optional.of(t);
        }
        return Optional.empty();
    }

    public static List<TypeMirror> getClassArrayTypeMirrors(AnnotationValue annotationValue) {
        List<TypeMirror> typeMirrors = new ArrayList<>();
        if (annotationValue == null) {
            return typeMirrors;
        }

        @SuppressWarnings("unchecked")
        List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) annotationValue.getValue();

        for (AnnotationValue val : values) {
            if (val.getValue() instanceof TypeMirror t) {
                typeMirrors.add(t);
            }
        }
        return typeMirrors;
    }
}