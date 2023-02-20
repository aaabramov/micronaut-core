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
import io.micronaut.core.util.ArrayUtils
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.getVisibility
import io.micronaut.kotlin.processing.kspNode
import io.micronaut.kotlin.processing.unwrap
import java.util.*
import java.util.function.Supplier
import kotlin.jvm.Throws

@OptIn(KspExperimental::class)
open class KotlinMethodElement : AbstractKotlinElement<KSAnnotated>, MethodElement {

    private val name: String
    private val owningType: ClassElement
    private val internalDeclaringType: ClassElement by lazy {
        var parent = declaration.parent
        if (parent is KSPropertyDeclaration) {
            parent = parent.parent
        }
        val owner = getOwningType()
        if (parent is KSClassDeclaration) {
            val className = parent.getBinaryName(visitorContext.resolver, visitorContext)
            if (owner.name.equals(className)) {
                owner
            } else {
                val parentTypeArguments = owner.getTypeArguments(className)
                newClassElement(parent.asStarProjectedType(), parentTypeArguments)
            }
        } else {
            owner
        }
    }
    private val internalDeclaredTypeArguments: Map<String, ClassElement> by lazy {
        val nativeType = kspNode()
        if (nativeType is KSDeclaration) {
            resolveTypeArguments(nativeType, emptyMap())
        } else {
            emptyMap()
        }
    }

    private var parameterInit: Supplier<List<ParameterElement>> = Supplier { emptyList() }
    private val parameters: List<ParameterElement> by lazy {
        parameterInit.get()
    }
    private val returnType: ClassElement
    private val internalGenericReturnType: ClassElement by lazy {
        when (val rt = getReturnType()) {
            is KotlinClassElement -> {
                newClassElement(rt.kotlinType, declaringType.typeArguments)
            }

            else -> {
                rt
            }
        }
    }
    private val abstract: Boolean
    private val public: Boolean
    private val private: Boolean
    private val protected: Boolean
    private val internal: Boolean
    private val propertyElement: KotlinPropertyElement?

    constructor(
        propertyType: ClassElement,
        propertyElement: KotlinPropertyElement,
        method: KSPropertySetter,
        owningType: ClassElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext
    ) : super(KSPropertySetterReference(method), elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.propertyElement = propertyElement
        this.owningType = owningType
        this.returnType = PrimitiveElement.VOID
        this.abstract = method.receiver.isAbstract()
        val visibility = method.getVisibility()
        this.public = visibility == Visibility.PUBLIC
        this.private = visibility == Visibility.PRIVATE
        this.protected = visibility == Visibility.PROTECTED
        this.internal = visibility == Visibility.INTERNAL
        this.parameterInit = Supplier {
            val parameterElement = KotlinParameterElement(
                propertyType, this, method.parameter, elementAnnotationMetadataFactory, visitorContext
            )
            listOf(parameterElement)
        }
    }

    constructor(
        propertyElement: KotlinPropertyElement,
        method: KSPropertyGetter,
        owningType: ClassElement,
        returnType: ClassElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
    ) : super(KSPropertyGetterReference(method), elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.propertyElement = propertyElement
        this.owningType = owningType
        this.parameterInit = Supplier { emptyList() }
        this.returnType = returnType
        this.abstract = method.receiver.isAbstract()
        this.public = method.receiver.isPublic()
        this.private = method.receiver.isPrivate()
        this.protected = method.receiver.isProtected()
        this.internal = method.receiver.isInternal()
    }

    constructor(
        method: KSFunctionDeclaration,
        owningType: ClassElement,
        returnType: ClassElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext
    ) : super(KSFunctionReference(method), elementAnnotationMetadataFactory, visitorContext) {
        this.name = visitorContext.resolver.getJvmName(method)!!
        this.owningType = owningType
        this.parameterInit = Supplier {
            method.parameters.map {
                val t = visitorContext.elementFactory.newClassElement(
                    it.type.resolve(),
                    elementAnnotationMetadataFactory
                )
                KotlinParameterElement(
                    t,
                    this,
                    it,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
            }
        }
        this.propertyElement = null
        this.returnType = returnType
        this.abstract = method.isAbstract
        this.public = method.isPublic()
        this.private = method.isPrivate()
        this.protected = method.isProtected()
        this.internal = method.isInternal()
    }

    protected constructor(
        method: KSAnnotated,
        name: String,
        owningType: ClassElement,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        returnType: ClassElement,
        parameters: List<ParameterElement>,
        abstract: Boolean,
        public: Boolean,
        private: Boolean,
        protected: Boolean,
        internal: Boolean
    ) : super(method, elementAnnotationMetadataFactory, visitorContext) {
        this.name = name
        this.owningType = owningType
        this.parameterInit = Supplier {
            parameters
        }
        this.propertyElement = null
        this.returnType = returnType
        this.abstract = abstract
        this.public = public
        this.private = private
        this.protected = protected
        this.internal = internal
    }

    override fun isSynthetic(): Boolean {
        return when (declaration) {
            is KSPropertyGetter, is KSPropertySetter -> {
                true
            }
            is KSFunctionDeclaration -> {
                declaration.functionKind != FunctionKind.MEMBER && declaration.functionKind != FunctionKind.STATIC
            }
            else -> {
                false
            }
        }
    }

    override fun isFinal() = if (declaration is KSPropertyGetter || declaration is KSPropertySetter) {
        true
    } else {
        super<AbstractKotlinElement>.isFinal()
    }

    override fun getModifiers() = super<AbstractKotlinElement>.getModifiers()

    override fun getDeclaredTypeArguments() = internalDeclaredTypeArguments

    override fun getDeclaredTypeVariables() =
        declaredTypeArguments.values.map { it as GenericPlaceholderElement }.toMutableList()

    override fun isSuspend(): Boolean {
        val nativeType = nativeType
        return if (nativeType is KSModifierListOwner) {
            nativeType.modifiers.contains(Modifier.SUSPEND)
        } else {
            false
        }
    }

    override fun getSuspendParameters(): Array<ParameterElement> {
        val parameters = getParameters()
        return if (isSuspend) {
            val continuationParameter = visitorContext.getClassElement("kotlin.coroutines.Continuation")
                .map {
                    var rt = internalGenericReturnType
                    if (rt.isPrimitive && rt.name.equals("void")) {
                        rt = ClassElement.of(Unit::class.java)
                    }
                    val resolvedType = it.withTypeArguments(mapOf("T" to rt))
                    ParameterElement.of(
                        resolvedType,
                        "continuation"
                    )
                }.orElse(null)
            if (continuationParameter != null) {
                ArrayUtils.concat(parameters, continuationParameter)
            } else {
                parameters
            }
        } else {
            parameters
        }
    }

    override fun overrides(overridden: MethodElement): Boolean {
        val nativeType = kspNode()
        val overriddenNativeType = overridden.kspNode()
        if (nativeType == overriddenNativeType) {
            return false
        } else if (nativeType is KSFunctionDeclaration) {
            return overriddenNativeType == nativeType.findOverridee()
        } else if (nativeType is KSPropertySetter && overriddenNativeType is KSPropertySetter) {
            return overriddenNativeType.receiver == nativeType.receiver.findOverridee()
        }
        return false
    }

    override fun hides(memberElement: MemberElement?) = // not sure how to implement this correctly for Kotlin
        false

    override fun withNewOwningType(owningType: ClassElement): MethodElement {
        val newMethod = KotlinMethodElement(
            declaration,
            name,
            owningType as KotlinClassElement,
            annotationMetadataFactory,
            visitorContext,
            returnType,
            parameters,
            abstract,
            public,
            private,
            protected,
            internal
        )
        copyValues(newMethod)
        return newMethod
    }

    override fun getName() = name

    override fun getOwningType() = owningType

    override fun getDeclaringType() = internalDeclaringType

    override fun getReturnType() = returnType

    override fun getGenericReturnType() = internalGenericReturnType

    override fun getParameters() = parameters.toTypedArray()

    override fun isAbstract() = abstract

    override fun isPublic() = public

    override fun isProtected() = protected

    override fun isPrivate() = private

    override fun copyThis(): KotlinMethodElement {
        if (declaration is KSPropertySetter) {
            return KotlinMethodElement(
                parameters[0].type,
                propertyElement!!,
                declaration.unwrap() as KSPropertySetter,
                owningType,
                annotationMetadataFactory,
                visitorContext
            )
        } else if (declaration is KSPropertyGetter) {
            return KotlinMethodElement(
                propertyElement!!,
                declaration.unwrap() as KSPropertyGetter,
                owningType,
                returnType,
                annotationMetadataFactory,
                visitorContext
            )
        } else if (declaration is KSFunctionDeclaration) {
            return KotlinMethodElement(
                declaration.unwrap() as KSFunctionDeclaration,
                owningType,
                returnType,
                annotationMetadataFactory,
                visitorContext
            )
        } else {
            return KotlinMethodElement(
                declaration,
                name,
                owningType,
                annotationMetadataFactory,
                visitorContext,
                returnType,
                parameters,
                abstract,
                public,
                private,
                protected,
                internal
            )
        }
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as MethodElement

    override fun toString(): String {
        return "$simpleName(" + parameters.joinToString(",") {
            if (it.type.isGenericPlaceholder) {
                (it.type as GenericPlaceholderElement).variableName
            } else {
                it.genericType.name
            }
        } + ")"
    }

    override fun withParameters(vararg newParameters: ParameterElement) =
        KotlinMethodElement(
            declaration,
            name,
            owningType,
            annotationMetadataFactory,
            visitorContext,
            returnType,
            newParameters.toList(),
            abstract,
            public,
            private,
            protected,
            internal
        )

    override fun getThrownTypes() = stringValues(Throws::class.java, "exceptionClasses")
        .flatMap {
            val ce = visitorContext.getClassElement(it).orElse(null)
            if (ce != null) {
                listOf(ce)
            } else {
                emptyList()
            }
        }.toTypedArray()

}
