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

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.ElementMutableAnnotationMetadataDelegate
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import io.micronaut.kotlin.processing.unwrap
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

abstract class AbstractKotlinElement<T : KSNode>(
    val declaration: T,
    protected val annotationMetadataFactory: ElementAnnotationMetadataFactory,
    protected val visitorContext: KotlinVisitorContext
) : Element, ElementMutableAnnotationMetadataDelegate<Element> {

    private var presetAnnotationMetadata: AnnotationMetadata? = null
    private var elementAnnotationMetadata: ElementAnnotationMetadata? = null

    override fun getNativeType(): T = declaration

    override fun isProtected() = if (declaration is KSDeclaration) {
        declaration.getVisibility() == Visibility.PROTECTED
    } else {
        false
    }

    override fun isStatic() = if (declaration is KSDeclaration) {
        declaration.modifiers.contains(Modifier.JAVA_STATIC)
    } else {
        false
    }

    protected fun makeCopy(): AbstractKotlinElement<T> {
        val element: AbstractKotlinElement<T> = copyThis()
        copyValues(element)
        return element
    }

    /**
     * @return copy of this element
     */
    protected abstract fun copyThis(): AbstractKotlinElement<T>

    /**
     * @param element the values to be copied to
     */
    protected open fun copyValues(element: AbstractKotlinElement<T>) {
        element.presetAnnotationMetadata = presetAnnotationMetadata
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): Element? {
        val kotlinElement: AbstractKotlinElement<T> = makeCopy()
        kotlinElement.presetAnnotationMetadata = annotationMetadata
        return kotlinElement
    }

    override fun getAnnotationMetadata(): MutableAnnotationMetadataDelegate<*> {
        if (elementAnnotationMetadata == null) {
            val factory = annotationMetadataFactory
            elementAnnotationMetadata = if (presetAnnotationMetadata == null) {
                factory.build(this)
            } else {
                factory.build(this, presetAnnotationMetadata)
            }
        }
        return elementAnnotationMetadata!!
    }

    override fun isPublic() = if (declaration is KSDeclaration) {
        declaration.getVisibility() == Visibility.PUBLIC
    } else {
        false
    }

    override fun isPrivate() = if (declaration is KSDeclaration) {
        declaration.getVisibility() == Visibility.PRIVATE
    } else {
        false
    }

    override fun isPackagePrivate() = if (declaration is KSDeclaration) {
        declaration.isJavaPackagePrivate()
    } else {
        false
    }

    override fun isFinal() = if (declaration is KSDeclaration) {
        !declaration.isOpen() || declaration.modifiers.contains(Modifier.FINAL)
    } else {
        false
    }

    override fun isAbstract(): Boolean {
        return if (declaration is KSModifierListOwner) {
            declaration.modifiers.contains(Modifier.ABSTRACT)
        } else {
            false
        }
    }

    @OptIn(KspExperimental::class)
    override fun getModifiers(): MutableSet<ElementModifier> {
        val dec = declaration.unwrap()
        if (dec is KSDeclaration) {
            val javaModifiers = visitorContext.resolver.effectiveJavaModifiers(dec)
            return javaModifiers.mapNotNull {
                when (it) {
                    Modifier.ABSTRACT -> ElementModifier.ABSTRACT
                    Modifier.FINAL -> ElementModifier.FINAL
                    Modifier.PRIVATE -> ElementModifier.PRIVATE
                    Modifier.PROTECTED -> ElementModifier.PROTECTED
                    Modifier.PUBLIC, Modifier.INTERNAL -> ElementModifier.PUBLIC
                    Modifier.JAVA_STATIC -> ElementModifier.STATIC
                    Modifier.JAVA_TRANSIENT -> ElementModifier.TRANSIENT
                    Modifier.JAVA_DEFAULT -> ElementModifier.DEFAULT
                    Modifier.JAVA_SYNCHRONIZED -> ElementModifier.SYNCHRONIZED
                    Modifier.JAVA_VOLATILE -> ElementModifier.VOLATILE
                    Modifier.JAVA_NATIVE -> ElementModifier.NATIVE
                    Modifier.JAVA_STRICT -> ElementModifier.STRICTFP
                    else -> null
                }
            }.toMutableSet()
        }
        return super.getModifiers()
    }

    override fun <T : Annotation?> annotate(
        annotationType: String?,
        consumer: Consumer<AnnotationValueBuilder<T>>?
    ): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType, consumer)
    }

    override fun annotate(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType)
    }

    override fun <T : Annotation?> annotate(
        annotationType: Class<T>?,
        consumer: Consumer<AnnotationValueBuilder<T>>?
    ): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType, consumer)
    }

    override fun <T : Annotation?> annotate(annotationType: Class<T>?): Element? {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType)
    }

    override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationValue)
    }

    override fun removeAnnotation(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotation(annotationType: Class<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotationIf(predicate)
    }

    override fun removeStereotype(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeStereotype(annotationType)
    }

    override fun <T : Annotation?> removeStereotype(annotationType: Class<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeStereotype(annotationType)
    }

    override fun getDocumentation(): Optional<String> {
        return if (declaration is KSDeclaration) {
            Optional.ofNullable(declaration.docString)
        } else {
            Optional.empty()
        }
    }

    override fun getReturnInstance(): Element {
        return this
    }

    protected fun resolveTypeParameter(
        typeParameter: KSTypeParameter,
        parentTypeArguments: Map<String, ClassElement>,
    ) = resolveTypeParameter(typeParameter, parentTypeArguments, HashSet())

    protected fun resolveTypeArguments(
        type: KSDeclaration,
        parentTypeArguments: Map<String, ClassElement>
    ) = resolveTypeArguments(type, parentTypeArguments, HashSet())

    private fun resolveTypeArguments(
        type: KSDeclaration,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): Map<String, ClassElement> {
        val typeArguments = mutableMapOf<String, ClassElement>()
        val typeParameters = type.typeParameters
        typeParameters.forEachIndexed { i, typeParameter ->
            typeArguments[typeParameters[i].name.asString()] =
                resolveTypeParameter(typeParameter, parentTypeArguments, visitedTypes)
        }
        return typeArguments
    }

    private fun resolveTypeParameter(
        typeParameter: KSTypeParameter,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): ClassElement {
        val variableName = typeParameter.name.asString()
        val bounded = parentTypeArguments[variableName] as KotlinClassElement?
        val stripTypeArguments = !visitedTypes.add(typeParameter)
        val bounds = typeParameter.bounds.map {
            val argumentType = it.resolve()
            newKotlinClassElement(argumentType, parentTypeArguments, visitedTypes, stripTypeArguments)
        }.ifEmpty {
            mutableListOf(getJavaObjectClassElement()).asSequence()
        }.toList()

        return KotlinGenericPlaceholderElement(
            typeParameter,
            bounded,
            bounds,
            0,
            annotationMetadataFactory,
            visitorContext
        )
    }

    private fun getJavaObjectClassElement() =
        visitorContext.getClassElement(Object::class.java.name).get() as KotlinClassElement

    private fun resolveTypeArguments(
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): Map<String, ClassElement> {
        val typeArguments = mutableMapOf<String, ClassElement>()
        val typeParameters = type.declaration.typeParameters
        if (type.arguments.isEmpty()) {
            typeParameters.forEach {
                typeArguments[it.name.asString()] = resolveTypeParameter(it, parentTypeArguments, visitedTypes)
            }
        } else {
            type.arguments.forEachIndexed { i, typeArgument ->
                val variableName = typeParameters[i].name.asString()
                val bounded = parentTypeArguments[variableName]
                if (bounded == null) {
                    typeArguments[variableName] = resolveTypeArgument(typeArgument, parentTypeArguments, visitedTypes)
                } else {
                    typeArguments[variableName] = bounded
                }
            }
        }
        return typeArguments
    }

    private fun resolveEmptyTypeArguments(type: KSType): Map<String, ClassElement> {
        val objectElement = visitorContext.getClassElement(Object::class.java.name).get()
        val typeArguments = mutableMapOf<String, ClassElement>()
        val typeParameters = type.declaration.typeParameters
        typeParameters.forEach {
            typeArguments[it.name.asString()] = objectElement
        }
        return typeArguments
    }

    private fun resolveTypeArgument(
        typeArgument: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): ClassElement {
        return when (typeArgument.variance) {
            Variance.STAR, Variance.COVARIANT, Variance.CONTRAVARIANT -> KotlinWildcardElement( // example List<*>
                typeArgument,
                resolveUpperBounds(typeArgument, parentTypeArguments, visitedTypes),
                resolveLowerBounds(typeArgument, parentTypeArguments, visitedTypes),
                annotationMetadataFactory,
                visitorContext,
                typeArgument.variance == Variance.STAR
            )
            // other cases
            else -> newKotlinClassElement(typeArgument.type!!.resolve(), parentTypeArguments, visitedTypes)
        }
    }

    private fun resolveLowerBounds(
        typeArgument: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): List<KotlinClassElement?> {
        return if (typeArgument.variance == Variance.CONTRAVARIANT) {
            listOf(
                newKotlinClassElement(typeArgument.type?.resolve()!!, parentTypeArguments, visitedTypes)
            )
        } else {
            return emptyList()
        }
    }

    private fun resolveUpperBounds(
        arg: KSTypeArgument,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ): List<KotlinClassElement?> {
        return if (arg.variance == Variance.COVARIANT) {
            listOf(
                newKotlinClassElement(arg.type?.resolve()!!, parentTypeArguments, visitedTypes)
            )
        } else {
            val objectType = visitorContext.resolver.getClassDeclarationByName(Object::class.java.name)!!
            listOf(
                newKotlinClassElement(objectType.asStarProjectedType(), parentTypeArguments, visitedTypes)
            )
        }
    }

    protected fun newKotlinClassElement(
        annotated: KSAnnotated,
        parentTypeArguments: Map<String, ClassElement>,
    ) = newKotlinClassElement(annotated, parentTypeArguments, HashSet())

    protected fun newKotlinClassElement(
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
    ) = newKotlinClassElement(type, parentTypeArguments, HashSet())

    private fun newKotlinClassElement(
        annotated: KSAnnotated,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>
    ) = newClassElement(annotated, parentTypeArguments, visitedTypes, false) as KotlinClassElement

    private fun newKotlinClassElement(
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>,
        stripTypeArguments: Boolean = false,
    ) = newClassElement(type, parentTypeArguments, visitedTypes, false, stripTypeArguments) as KotlinClassElement

    protected fun newClassElement(
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
    ) = newClassElement(type, parentTypeArguments, HashSet())

    protected fun newClassElement(
        annotated: KSAnnotated,
        parentTypeArguments: Map<String, ClassElement>,
    ) = newClassElement(annotated, parentTypeArguments, HashSet())

    private fun newClassElement(
        type: KSType,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>,
        allowPrimitive: Boolean = true,
        stripTypeArguments: Boolean = false
    ): ClassElement {
        val declaration = type.declaration
        val qualifiedName = declaration.qualifiedName!!.asString()
        val hasNoAnnotations = !type.annotations.iterator().hasNext()
        var element = KotlinElementFactory.primitiveArrays[qualifiedName]
        if (hasNoAnnotations && element != null) {
            return element
        }
        if (qualifiedName == "kotlin.Array") {
            val component = type.arguments[0].type!!.resolve()
            val componentElement = newClassElement(component, parentTypeArguments, visitedTypes, allowPrimitive)
            return componentElement.toArray()
        } else if (declaration is KSTypeParameter) {
            return resolveTypeParameter(declaration, parentTypeArguments, visitedTypes)
        }
        if (allowPrimitive && !type.isMarkedNullable) {
            element = KotlinElementFactory.primitives[qualifiedName]
            if (hasNoAnnotations && element != null) {
                return element
            }
        }
        val typeArguments = if (stripTypeArguments) resolveEmptyTypeArguments(type) else resolveTypeArguments(type, parentTypeArguments, visitedTypes)
        return if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
            KotlinEnumElement(type, annotationMetadataFactory, visitorContext, typeArguments)
        } else {
            KotlinClassElement(type, annotationMetadataFactory, visitorContext, typeArguments)
        }
    }

    private fun newClassElement(
        annotated: KSAnnotated,
        parentTypeArguments: Map<String, ClassElement>,
        visitedTypes: MutableSet<Any>,
        allowPrimitive: Boolean = true
    ): ClassElement {
        val type = KotlinClassElement.getType(annotated, visitorContext)
        val declaration = type.declaration
        val qualifiedName = declaration.qualifiedName!!.asString()
        val hasNoAnnotations = !annotated.annotations.iterator().hasNext()
        var element = KotlinElementFactory.primitiveArrays[qualifiedName]
        if (hasNoAnnotations && element != null) {
            return element
        }
        if (qualifiedName == "kotlin.Array") {
            val component = type.arguments[0].type!!.resolve()
            val componentElement = newClassElement(component, parentTypeArguments, visitedTypes, allowPrimitive)
            return componentElement.toArray()
        } else if (declaration is KSTypeParameter) {
            return resolveTypeParameter(declaration, parentTypeArguments, visitedTypes)
        }
        if (allowPrimitive && !type.isMarkedNullable) {
            element = KotlinElementFactory.primitives[qualifiedName]
            if (hasNoAnnotations && element != null) {
                return element
            }
        }
        val typeArguments = resolveTypeArguments(type, parentTypeArguments, visitedTypes)
        return if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
            KotlinEnumElement(type, annotationMetadataFactory, visitorContext, typeArguments)
        } else {
            KotlinClassElement(annotated, annotationMetadataFactory, visitorContext, typeArguments)
        }
    }

//    protected fun resolveGeneric(
//        parent: KSNode?,
//        type: ClassElement,
//        owningClass: ClassElement,
//        visitorContext: KotlinVisitorContext
//    ): ClassElement {
//        var resolvedType = type
//        if (parent is KSDeclaration && owningClass is KotlinClassElement) {
//            if (type is GenericPlaceholderElement) {
//                val variableName = type.variableName
//                val genericTypeInfo = owningClass.getGenericTypeInfo()
//                val boundInfo = genericTypeInfo[parent.getBinaryName(visitorContext.resolver, visitorContext)]
//                if (boundInfo != null) {
//                    val ksType = boundInfo[variableName]
//                    if (ksType != null) {
//                        resolvedType = newClassElement(ksType, emptyMap())
//                        if (type.isArray) {
//                            resolvedType = resolvedType.toArray()
//                        }
//                    }
//                }
//            } else if (type.declaredGenericPlaceholders.isNotEmpty() && type is KotlinClassElement) {
//                val genericTypeInfo = owningClass.getGenericTypeInfo()
//                val kotlinType = type.kotlinType
//                val boundInfo = if (parent.qualifiedName != null)  genericTypeInfo[parent.getBinaryName(visitorContext.resolver, visitorContext)] else null
//                resolvedType = if (boundInfo != null) {
//                    val boundArgs = kotlinType.arguments.map { arg ->
//                        resolveTypeArgument(arg, boundInfo, visitorContext)
//                    }.toMutableList()
//                    type.withBoundGenericTypes(boundArgs)
//                } else {
//                    type
//                }
//            }
//        }
//        return resolvedType
//    }

//    private fun resolveTypeArgument(
//        arg: KSTypeArgument,
//        boundInfo: Map<String, KSType>,
//        visitorContext: KotlinVisitorContext
//    ): ClassElement {
//        val n = arg.type?.toString()
//        val resolved = boundInfo[n]
//        return if (resolved != null) {
//            newKotlinClassElement(resolved, emptyMap())
//        } else {
//            if (arg.type != null) {
//                val t = arg.type!!.resolve()
//                if (t.arguments.isNotEmpty()) {
//                    newKotlinClassElement(
//                        t,
//                        emptyMap()
//                    )
////                        .withBoundGenericTypes(
////                        t.arguments.map {
////                            resolveTypeArgument(it, boundInfo, visitorContext)
////                        }
////                    )
//                } else {
//                    newKotlinClassElement(t, emptyMap())
//                }
//            } else {
//                visitorContext.getClassElement(Object::class.java.name).get()
//            }
//        }
//    }

    override fun toString(): String {
        return getDescription(false)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractKotlinElement<*>) return false
        if (nativeType != other.nativeType) return false
        return true
    }

    override fun hashCode(): Int {
        return nativeType.hashCode()
    }

}
