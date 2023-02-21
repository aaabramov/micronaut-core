/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataDelegate
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.kspNode
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

class KotlinPropertyElement: AbstractKotlinElement<KSNode>, PropertyElement {

    private val name: String
    private val classElement: ClassElement
    private var presetType: ClassElement? = null
    private var propertyDeclaration: KSPropertyDeclaration? = null

    private val resolvedType: ClassElement by lazy {
        if (presetType != null) {
            presetType!!
        } else {
            newClassElement(propertyDeclaration!!.type.resolve(), emptyMap())
        }
    }
    private val resolvedGenericType: ClassElement by lazy {
        if (presetType != null) {
            presetType!!
        } else {
            newClassElement(propertyDeclaration!!.type.resolve(), declaringType.typeArguments)
        }
    }
    private val setter: Optional<MethodElement>
    private val getter: Optional<MethodElement>
    private val field: Optional<FieldElement>
    private val abstract: Boolean
    private val excluded: Boolean
    private var annotationMetadata: MutableAnnotationMetadataDelegate<*>? = null
    private val resolvedDeclaringType: ClassElement by lazy {
        var parent = declaration.parent
        if (parent is KSPropertyDeclaration) {
            parent = parent.parent
        }
        if (parent is KSClassDeclaration) {
            val className = parent.getBinaryName(visitorContext.resolver, visitorContext)
            if (owningType.name.equals(className)) {
                owningType
            } else {
                val parentTypeArguments = owningType.getTypeArguments(className)
                newClassElement(parent.asStarProjectedType(), parentTypeArguments)
            }
        } else {
            owningType
        }
    }

    constructor(classElement: ClassElement,
                property: KSPropertyDeclaration,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext,
                excluded : Boolean = false) : super(KSPropertyReference(property), elementAnnotationMetadataFactory, visitorContext) {
        this.propertyDeclaration = property
        this.name = property.simpleName.asString()
        this.excluded = excluded
        this.classElement = classElement
        this.setter = Optional.ofNullable(property.setter)
            .map { method ->
                val modifiers = try {
                    method.modifiers
                } catch (e: IllegalStateException) {
                    // KSP bug: IllegalStateException: unhandled visibility: invisible_fake
                    setOf(Modifier.INTERNAL)
                }
                return@map if (modifiers.contains(Modifier.PRIVATE)) {
                    null
                } else {
                    visitorContext.elementFactory.newMethodElement(classElement, this, method, type, elementAnnotationMetadataFactory)
                }
            }
        this.getter = Optional.ofNullable(property.getter)
            .map { method ->
                return@map visitorContext.elementFactory.newMethodElement(classElement, this, method, type, elementAnnotationMetadataFactory)
            }
        this.abstract = property.isAbstract()
        if (property.hasBackingField) {
            val newFieldElement = visitorContext.elementFactory.newFieldElement(
                classElement,
                property,
                elementAnnotationMetadataFactory
            )
            this.field = Optional.of(newFieldElement)
        } else {
            this.field = Optional.empty()
        }

        val elements: MutableList<MemberElement> = ArrayList(3)
        setter.ifPresent { elements.add(it) }
        getter.ifPresent { elements.add(it) }
        field.ifPresent { elements.add(it) }

        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        val propertyAnnotationMetadata: AnnotationMetadata = if (elements.size == 1) {
            elements.iterator().next()
        } else {
            AnnotationMetadataHierarchy(
                true,
                *elements.map { e: MemberElement ->
                    if (e is MethodElement) {
                        return@map object : AnnotationMetadataDelegate {
                            override fun getAnnotationMetadata(): AnnotationMetadata = // Exclude type metadata
                                e.getAnnotationMetadata().declaredMetadata
                        }
                    }
                    e
                }.toTypedArray()
            )
        }
        this.annotationMetadata = object : MutableAnnotationMetadataDelegate<Any?> {
            override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationValue)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: String,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(annotationType: Class<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun annotate(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: Class<T>,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun removeAnnotation(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotation(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotationIf(predicate)
                }
                return this@KotlinPropertyElement
            }

            override fun getAnnotationMetadata(): AnnotationMetadata = propertyAnnotationMetadata
        }
    }
    constructor(classElement: ClassElement,
                type: ClassElement,
                name: String,
                getter: KSFunctionDeclaration,
                setter: KSFunctionDeclaration?,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext,
                excluded : Boolean = false) : super(getter, elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.presetType = type
        this.excluded = excluded
        this.classElement = classElement
        this.setter = Optional.ofNullable(setter)
            .map { method ->
                visitorContext.elementFactory.newMethodElement(classElement, method, elementAnnotationMetadataFactory)
            }
        this.getter = Optional.of(visitorContext.elementFactory.newMethodElement(classElement, getter, elementAnnotationMetadataFactory))
        this.abstract = getter.isAbstract || setter?.isAbstract == true
        this.field = Optional.empty()
        val elements: MutableList<MemberElement> = ArrayList(3)
        this.setter.ifPresent { elements.add(it) }
        this.getter.ifPresent { elements.add(it) }
        field.ifPresent { elements.add(it) }

        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        val propertyAnnotationMetadata: AnnotationMetadata = if (elements.size == 1) {
            elements.iterator().next()
        } else {
            AnnotationMetadataHierarchy(
                true,
                *elements.stream().map { e: MemberElement ->
                    if (e is MethodElement) {
                        return@map object : AnnotationMetadataDelegate {
                            override fun getAnnotationMetadata(): AnnotationMetadata = // Exclude type metadata
                                e.getAnnotationMetadata().declaredMetadata
                        }
                    }
                    e
                }.toList().toTypedArray()
            )
        }
        this.annotationMetadata = object : MutableAnnotationMetadataDelegate<Any?> {
            override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationValue)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: String,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(annotationType: Class<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun annotate(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: Class<T>,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun removeAnnotation(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotation(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotationIf(predicate)
                }
                return this@KotlinPropertyElement
            }

            override fun getAnnotationMetadata(): AnnotationMetadata = propertyAnnotationMetadata
        }
    }

    constructor(classElement: ClassElement,
                type: ClassElement,
                name: String,
                field: FieldElement?,
                getter: MethodElement?,
                setter: MethodElement?,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext,
                excluded : Boolean = false) : super(pickDeclaration(type, field, getter, setter), elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.presetType = type
        this.classElement = classElement
        this.setter = Optional.ofNullable(setter)
        this.getter = Optional.ofNullable(getter)
        this.abstract = getter?.isAbstract == true || setter?.isAbstract == true
        this.field = Optional.ofNullable(field)
        val elements: MutableList<MemberElement> = ArrayList(3)
        this.setter.ifPresent { elements.add(it) }
        this.getter.ifPresent { elements.add(it) }
        this.field.ifPresent { elements.add(it) }
        this.excluded = excluded

        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        val propertyAnnotationMetadata: AnnotationMetadata = if (elements.size == 1) {
            elements.iterator().next().declaredMetadata
        } else {
            AnnotationMetadataHierarchy(
                true,
                *elements.stream().map { e: MemberElement ->
                    if (e is MethodElement) {
                        return@map object : AnnotationMetadataDelegate {
                            override fun getAnnotationMetadata(): AnnotationMetadata = // Exclude type metadata
                                e.getAnnotationMetadata().declaredMetadata
                        }
                    }
                    e
                }.toList().toTypedArray()
            )
        }
        this.annotationMetadata = object : MutableAnnotationMetadataDelegate<Any?> {
            override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationValue)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: String,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(annotationType: Class<T>): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun annotate(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> annotate(
                annotationType: Class<T>,
                consumer: Consumer<AnnotationValueBuilder<T>>
            ): Element {
                for (memberElement in elements) {
                    memberElement.annotate(annotationType, consumer)
                }
                return this@KotlinPropertyElement
            }

            override fun removeAnnotation(annotationType: String): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotation(annotationType)
                }
                return this@KotlinPropertyElement
            }

            override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>): Element {
                for (memberElement in elements) {
                    memberElement.removeAnnotationIf(predicate)
                }
                return this@KotlinPropertyElement
            }

            override fun getAnnotationMetadata(): AnnotationMetadata = propertyAnnotationMetadata
        }
    }

    companion object Helper {
        private fun pickDeclaration(
            type: ClassElement,
            field: FieldElement?,
            getter: MethodElement?,
            setter: MethodElement?
        ): KSNode {
            return if (field?.nativeType != null) {
                field.nativeType as KSNode
            } else if (getter?.nativeType != null) {
                getter.nativeType as KSNode
            } else if (setter?.nativeType != null) {
                setter.nativeType as KSNode
            } else {
                type.nativeType as KSNode
            }
        }
    }

    override fun overrides(overridden: PropertyElement?): Boolean {
        if (overridden == null) {
            return false
        } else {
            val nativeType = kspNode()
            val overriddenNativeType = overridden.kspNode()
            if (nativeType == overriddenNativeType) {
                return false
            } else if (nativeType is KSPropertyDeclaration) {
                return overriddenNativeType == nativeType.findOverridee()
            }
            return false
        }
    }

    override fun isExcluded() = excluded

    override fun getGenericType() = resolvedGenericType

    override fun getAnnotationMetadata() = annotationMetadata!!

    override fun getField() = field

    override fun getName() = name

    override fun getModifiers() = super<AbstractKotlinElement>.getModifiers()

    override fun getType() = resolvedType

    override fun getDeclaringType() = resolvedDeclaringType

    override fun getOwningType() = classElement

    override fun getReadMethod() = getter

    override fun getWriteMethod() = setter

    override fun copyThis(): AbstractKotlinElement<KSNode> {
        if (nativeType is KSPropertyDeclaration) {
            val property : KSPropertyDeclaration = nativeType as KSPropertyDeclaration
            return KotlinPropertyElement(
                classElement,
                property,
                annotationMetadataFactory,
                visitorContext,
                excluded
            )
        } else {
            val getter : KSFunctionDeclaration = nativeType as KSFunctionDeclaration
            return KotlinPropertyElement(
                classElement,
                presetType!!,
                name,
                getter,
                setter.map { it.nativeType as KSFunctionDeclaration }.orElse(null),
                annotationMetadataFactory,
                visitorContext,
                excluded
            )
        }
    }

    override fun isAbstract() = abstract

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as MemberElement

    override fun isPrimitive() = type.isPrimitive

    override fun isArray() = type.isArray

    override fun getArrayDimensions() = type.arrayDimensions

    override fun isDeclaredNullable(): Boolean {
        val theType = resolvedType
        return theType is KotlinClassElement && theType.kotlinType.isMarkedNullable
    }

    override fun isNullable(): Boolean {
        val theType = resolvedType
        return theType is KotlinClassElement && theType.kotlinType.isMarkedNullable
    }

}
