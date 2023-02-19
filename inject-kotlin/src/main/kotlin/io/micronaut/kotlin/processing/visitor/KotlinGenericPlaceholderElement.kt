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

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.kotlin.processing.getBinaryName
import java.util.*
import java.util.function.Function

//JavaNativeElement.Placeholder genericNativeType,
//TypeVariable realTypeVariable,
//@NonNull Element declaredElement,
//@Nullable JavaClassElement resolved,
//@NonNull List<JavaClassElement> bounds,
//@NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
//int arrayDimensions,
//boolean isRawType

class KotlinGenericPlaceholderElement(
    private var parameter: KSTypeParameter,
    upper: KotlinClassElement,
    private var resolved: KotlinClassElement?,
    private var bounds: List<KotlinClassElement>,
    private var arrayDimensions: Int = 0,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : KotlinClassElement(upper.kotlinType,
    elementAnnotationMetadataFactory,
    visitorContext,
    upper.resolvedTypeArguments,
    arrayDimensions,
    true), ArrayableClassElement, GenericPlaceholderElement {

    constructor(parameter: KSTypeParameter,
                resolved: KotlinClassElement?,
                bounds: List<KotlinClassElement>,
                arrayDimensions: Int = 0,
                elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                visitorContext: KotlinVisitorContext
        ) : this(parameter, selectClassElementRepresentingThisPlaceholder(resolved, bounds), resolved, bounds, arrayDimensions, elementAnnotationMetadataFactory, visitorContext) {
            this.parameter = parameter;
        }

    override fun copyThis(): KotlinGenericPlaceholderElement {
        return KotlinGenericPlaceholderElement(
            parameter,
            resolved,
            bounds,
            arrayDimensions,
            annotationMetadataFactory,
            visitorContext)
    }

    override fun getName(): String {
        val bounds = parameter.bounds.firstOrNull()
        if (bounds != null) {
            return bounds.resolve().declaration.getBinaryName(visitorContext.resolver, visitorContext)
        }
        return "java.lang.Object"
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): ClassElement {
        return super<KotlinClassElement>.withAnnotationMetadata(annotationMetadata) as ClassElement
    }

    override fun isArray(): Boolean = arrayDimensions > 0

    override fun getArrayDimensions(): Int = arrayDimensions

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinGenericPlaceholderElement(
            parameter,
            resolved,
            bounds,
            arrayDimensions,
            annotationMetadataFactory,
            visitorContext)
    }

    override fun getBounds(): List<out ClassElement> {
        return bounds;
    }

    override fun getVariableName(): String {
        return parameter.simpleName.asString()
    }

    override fun getDeclaringElement(): Optional<Element> {
        val classDeclaration = parameter.closestClassDeclaration()
        return Optional.ofNullable(classDeclaration).map {
            visitorContext.elementFactory.newClassElement(
                classDeclaration!!.asStarProjectedType(),
                visitorContext.elementAnnotationMetadataFactory)
        }
    }

//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        if (!super.equals(other)) return false
//
//        other as KotlinGenericPlaceholderElement
//
//        if (parameter.simpleName.asString() != other.parameter.simpleName.asString()) return false
//
//        return true
//    }

//    override fun hashCode(): Int {
//        var result = super.hashCode()
//        result = 31 * result + parameter.simpleName.asString().hashCode()
//        return result
//    }

    override fun foldBoundGenericTypes(fold: Function<ClassElement, ClassElement>?): ClassElement {
        Objects.requireNonNull(fold, "Function argument cannot be null")
        return fold!!.apply(this)
    }

    companion object {
        private fun selectClassElementRepresentingThisPlaceholder(
            @Nullable resolved: KotlinClassElement?,
            @NonNull bounds: List<KotlinClassElement>
        ): KotlinClassElement {
            return resolved ?: WildcardElement.findUpperType(bounds, bounds)
        }
    }
}
