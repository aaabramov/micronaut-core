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

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getBinaryName

class KotlinFieldElement(
    declaration: KSPropertyDeclaration,
    private val owningType: ClassElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : AbstractKotlinElement<KSPropertyDeclaration>(
    KSPropertyReference(declaration),
    elementAnnotationMetadataFactory,
    visitorContext
), FieldElement {

    private val internalName = declaration.simpleName.asString()
    private val internalDeclaringType: ClassElement by lazy {
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
    private val internalKSType: KSType by lazy {
        declaration.type.resolve()
    }
    private val internalType: ClassElement by lazy {
        newClassElement(internalKSType, emptyMap())
    }
    private val internalGenericType: ClassElement by lazy {
        newClassElement(internalKSType, declaringType.typeArguments)
    }

    override fun isFinal() = declaration.setter == null

    override fun isReflectionRequired() = true // all Kotlin fields are private

    override fun isReflectionRequired(callingType: ClassElement?) = true // all Kotlin fields are private

    override fun isPublic() = if (hasDeclaredAnnotation(JvmField::class.java)) {
        super.isPublic()
    } else {
        false // all Kotlin fields are private
    }

    override fun getType() = internalType

    override fun getGenericType() = internalGenericType

    override fun getName() = internalName

    override fun isPrimitive() = type.isPrimitive

    override fun isArray() = type.isArray

    override fun getArrayDimensions() = type.arrayDimensions

    override fun copyThis() =
        KotlinFieldElement(declaration, owningType, annotationMetadataFactory, visitorContext)

    override fun isPrivate() = true

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as FieldElement

    override fun getOwningType() = owningType

    override fun getDeclaringType() = internalDeclaringType

    override fun getModifiers() = super<AbstractKotlinElement>.getModifiers()
}
