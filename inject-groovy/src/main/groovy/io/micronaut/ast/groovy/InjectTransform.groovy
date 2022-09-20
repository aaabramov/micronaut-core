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
package io.micronaut.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.ast.groovy.utils.InMemoryClassWriterOutputVisitor
import io.micronaut.ast.groovy.visitor.GroovyPackageElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.context.annotation.Configuration
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import io.micronaut.inject.processing.gen.AbstractBeanBuilder
import io.micronaut.inject.processing.gen.ProcessingException
import io.micronaut.inject.visitor.VisitorConfiguration
import io.micronaut.inject.writer.BeanConfigurationWriter
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.ClassWriterOutputVisitor
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.StringReaderSource
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.lang.reflect.Modifier
import java.util.function.Predicate
/**
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
// IMPORTANT NOTE: This transform runs in phase CANONICALIZATION so it runs after TypeElementVisitorTransform
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    public static final String ANN_VALID = "javax.validation.Valid"
    public static final String ANN_CONSTRAINT = "javax.validation.Constraint"
    public static final String ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice"
    public static final String ANN_VALIDATED = "io.micronaut.validation.Validated"
    public static final Predicate<AnnotationMetadata> IS_CONSTRAINT = (Predicate<AnnotationMetadata>) { AnnotationMetadata am ->
        am.hasStereotype(InjectTransform.ANN_CONSTRAINT) || am.hasStereotype(InjectTransform.ANN_VALID)
    }
    CompilationUnit unit
    ConfigurationMetadataBuilder configurationMetadataBuilder = ConfigurationMetadataBuilder.INSTANCE

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]
        File classesDir = source.configuration.targetDirectory
        boolean defineClassesInMemory = source.classLoader instanceof InMemoryByteCodeGroovyClassLoader
        ClassWriterOutputVisitor outputVisitor
        if (defineClassesInMemory) {
            outputVisitor = new InMemoryClassWriterOutputVisitor(
                    source.classLoader as InMemoryByteCodeGroovyClassLoader
            )

        } else {
            outputVisitor = new DirectoryClassWriterOutputVisitor(
                    classesDir
            )
        }
        List<ClassNode> classes = moduleNode.getClasses()
        if (classes.size() == 1) {
            ClassNode classNode = classes[0]
            if (classNode.nameWithoutPackage == 'package-info') {
                PackageNode packageNode = classNode.getPackage()
                GroovyVisitorContext visitorContext = new GroovyVisitorContext(source, unit)
                GroovyPackageElement groovyPackageElement = new GroovyPackageElement(visitorContext, packageNode, visitorContext.getElementAnnotationMetadataFactory())
                if (groovyPackageElement.hasStereotype(Configuration)) {
                    BeanConfigurationWriter writer = new BeanConfigurationWriter(
                            classNode.packageName,
                            groovyPackageElement,
                            groovyPackageElement.getAnnotationMetadata()
                    )
                    try {
                        writer.accept(outputVisitor)
                        outputVisitor.finish()
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, classNode, "Error generating bean configuration for package-info class [${classNode.name}]: $e.message")
                    }
                }

                return
            }
        }

        for (ClassNode classNode in classes) {
            if ((classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            } else {

                GroovyVisitorContext groovyVisitorContext = new GroovyVisitorContext(source, unit) {
                    @Override
                    VisitorConfiguration getConfiguration() {
                        new VisitorConfiguration() {
                            @Override
                            boolean includeTypeLevelAnnotationsInGenericArguments() {
                                return false
                            }
                        }
                    }
                }
                def elementAnnotationMetadataFactory = groovyVisitorContext
                        .getElementAnnotationMetadataFactory()
                        .readOnly()
                def thisElement = groovyVisitorContext.getElementFactory().newClassElement(classNode, elementAnnotationMetadataFactory)
                if (!classNode.isInterface() || classNode.isInterface() && (thisElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION) ||
                        thisElement.hasStereotype(ConfigurationReader.class))) {

                    try {
                        new ClassCodeVisitorSupport() {

                            @Override
                            void visitClass(ClassNode node) {
                                def classElement = node == thisElement.getNativeType() ? thisElement : groovyVisitorContext.getElementFactory().newClassElement(node, groovyVisitorContext.getElementAnnotationMetadataFactory())
                                AbstractBeanBuilder beanBuilder = AbstractBeanBuilder.of(classElement, groovyVisitorContext);
                                if (beanBuilder != null) {
                                    beanBuilder.build()
                                    beanBuilder.getBeanDefinitionWriters().forEach(writer -> {
                                        if (writer.getBeanTypeName() == node.getName()) {
                                            beanDefinitionWriters.put(node, writer)
                                        } else {
                                            beanDefinitionWriters.put(new AnnotatedNode(), writer)
                                        }
                                    })
                                }

                                super.visitClass(node)
                            }

                            @Override
                            protected SourceUnit getSourceUnit() {
                                return sourceUnit
                            }

                        }.visitClass(classNode)

                    } catch (ProcessingException ex) {
                        groovyVisitorContext.fail(ex.getMessage(), ex.getElement());
                    }
                }
            }
        }

        for (entry in beanDefinitionWriters) {
            BeanDefinitionVisitor beanDefWriter = entry.value
            String beanTypeName = beanDefWriter.beanTypeName
            AnnotatedNode beanClassNode = entry.key
            try {
                BeanDefinitionReferenceWriter beanReferenceWriter = new BeanDefinitionReferenceWriter(
                        beanDefWriter
                )

                beanReferenceWriter.setRequiresMethodProcessing(beanDefWriter.requiresMethodProcessing())
                beanReferenceWriter.setContextScope(beanDefWriter.getAnnotationMetadata().hasDeclaredAnnotation(Context))
                beanDefWriter.visitBeanDefinitionEnd()
                if (classesDir != null) {
                    beanReferenceWriter.accept(outputVisitor)
                    beanDefWriter.accept(outputVisitor)
                } else if (source.source instanceof StringReaderSource && defineClassesInMemory) {
                    beanReferenceWriter.accept(outputVisitor)
                    beanDefWriter.accept(outputVisitor)

                }


            } catch (Throwable e) {
                AstMessageUtils.error(source, beanClassNode, "Error generating bean definition class for dependency injection of class [${beanTypeName}]: $e.message")
                e.printStackTrace(System.err)
            }
        }
        if (!beanDefinitionWriters.isEmpty()) {

            try {
                outputVisitor.finish()

            } catch (Throwable e) {
                AstMessageUtils.error(source, moduleNode, "Error generating META-INF/services files: $e.message")
                if (e.message == null) {
                    e.printStackTrace(System.err)
                }
            }
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

}
