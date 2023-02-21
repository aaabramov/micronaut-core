/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AllElementsVisitor implements TypeElementVisitor<Object, Object> {
    public static List<String> VISITED_ELEMENTS = new ArrayList<>();
    public static List<ClassElement> VISITED_CLASS_ELEMENTS = new ArrayList<>();
    public static List<MethodElement> VISITED_METHOD_ELEMENTS = new ArrayList<>();

    @Override
    public void start(VisitorContext visitorContext) {
        VISITED_ELEMENTS.clear();
        VISITED_CLASS_ELEMENTS.clear();
        VISITED_METHOD_ELEMENTS.clear();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visit(element);
        // Preload annotations and elements for tests otherwise it fails because the compiler is done
        initializeClassElement(element, new HashSet<>());
        VISITED_CLASS_ELEMENTS.add(element);
    }

    @Override
    public void visitMethod(MethodElement methodElement, VisitorContext context) {
        VISITED_METHOD_ELEMENTS.add(methodElement);
        // Preload
        initializeMethodElement(methodElement, new HashSet<>());
        visit(methodElement);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        initializeTypedElement(element, new HashSet<>());
        visit(element);
    }

    private void initializeElement(Element typedElement) {
        typedElement.getAnnotationMetadata().getAnnotationNames();
    }

    private void initializeTypedElement(TypedElement typedElement, Set<Object> visited) {
        if (visited.contains(typedElement)) {
            return;
        }
        visited.add(typedElement);
        initializeTypedElementNoCache(typedElement, visited);
    }

    private void initializeTypedElementNoCache(TypedElement typedElement, Set<Object> visited) {
        initializeElement(typedElement);
        initializeClassElement(typedElement.getType(), visited);
        initializeClassElement(typedElement.getGenericType(), visited);
    }

    private void initializeClassElement(ClassElement classElement, Set<Object> visitedParam) {
        if (!classElement.getName().startsWith("test.")) {
            return;
        }
        if (visitedParam.contains(classElement)) {
            return;
        }
        Set<Object> visited = new HashSet<>(visitedParam);
        visited.add(classElement);

        initializeTypedElementNoCache(classElement, visited);
        classElement.getPrimaryConstructor().ifPresent(methodElement -> initializeMethodElement(methodElement, visitedParam));
        classElement.getSuperType().ifPresent(superType -> initializeClassElement(superType, visited));
        classElement.getFields().forEach(field -> initializeTypedElement(field, visited));
        classElement.getMethods().forEach(method -> initializeMethodElement(method, visited));
        classElement.getDeclaredGenericPlaceholders();
        classElement.getSyntheticBeanProperties();
        classElement.getBeanProperties().forEach(AnnotationMetadataProvider::getAnnotationMetadata);
        classElement.getBeanProperties().forEach(propertyElement -> {
            initializeTypedElement(propertyElement, visited);
            propertyElement.getField().ifPresent(f -> initializeTypedElement(f, visited));
            propertyElement.getWriteMethod().ifPresent(methodElement -> initializeMethodElement(methodElement, visited));
            propertyElement.getReadMethod().ifPresent(methodElement -> initializeMethodElement(methodElement, visited));
        });
        classElement.getAllTypeArguments().values().forEach(ta -> ta.values().forEach(ce -> initializeClassElement(ce, visited)));
    }

    private void initializeMethodElement(MethodElement methodElement, Set<Object> visitedParam) {
        if (visitedParam.contains(methodElement)) {
            return;
        }
        Set<Object> visited = new HashSet<>(visitedParam);
        visited.add(methodElement);

        initializeElement(methodElement);
        initializeClassElement(methodElement.getReturnType(), visited);
        initializeClassElement(methodElement.getGenericReturnType(), visited);
        Arrays.stream(methodElement.getParameters()).forEach(p -> initializeTypedElement(p, visited));
        methodElement.getDeclaredTypeArguments().values().forEach(c -> initializeClassElement(c, visited));
        methodElement.getTypeArguments().values().forEach(c -> initializeClassElement(c, visited));
    }

    private void visit(Element element) {
        VISITED_ELEMENTS.add(element.getName());
    }
}
