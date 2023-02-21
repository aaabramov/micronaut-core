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

import com.google.devtools.ksp.symbol.KSTypeParameter
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import java.util.*
import java.util.function.Function

class KotlinGenericPlaceholderElement(
    private var parameter: KSTypeParameter,
    private var upper: KotlinClassElement,
    private var resolved: KotlinClassElement?,
    private var bounds: List<KotlinClassElement>,
    private var declaringElement: Element?,
    arrayDimensions: Int = 0,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    visitorContext: KotlinVisitorContext
) : KotlinClassElement(
    upper.kotlinType,
    elementAnnotationMetadataFactory,
    visitorContext,
    upper.resolvedTypeArguments,
    arrayDimensions,
    true
), ArrayableClassElement, GenericPlaceholderElement {

    constructor(
        parameter: KSTypeParameter,
        resolved: KotlinClassElement?,
        bounds: List<KotlinClassElement>,
        declaringElement: Element?,
        arrayDimensions: Int = 0,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext
    ) : this(
        parameter,
        selectClassElementRepresentingThisPlaceholder(resolved, bounds),
        resolved,
        bounds,
        declaringElement,
        arrayDimensions,
        elementAnnotationMetadataFactory,
        visitorContext
    ) {
        this.parameter = parameter
    }

    override fun copyThis() = KotlinGenericPlaceholderElement(
        parameter,
        upper,
        resolved,
        bounds,
        declaringElement,
        arrayDimensions,
        annotationMetadataFactory,
        visitorContext
    )

    override fun isGenericPlaceholder() = true

    override fun getGenericNativeType() = parameter

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<KotlinClassElement>.withAnnotationMetadata(annotationMetadata)

    override fun withArrayDimensions(arrayDimensions: Int) = KotlinGenericPlaceholderElement(
        parameter,
        upper,
        resolved,
        bounds,
        declaringElement,
        arrayDimensions,
        annotationMetadataFactory,
        visitorContext
    )

    override fun getBounds() = bounds

    override fun getVariableName() = parameter.simpleName.asString()

    override fun getResolved(): Optional<ClassElement> = Optional.ofNullable(resolved)

    override fun getDeclaringElement(): Optional<Element> = Optional.ofNullable(declaringElement)

    override fun foldBoundGenericTypes(fold: Function<ClassElement, ClassElement?>) = fold.apply(this)

    companion object {
        private fun selectClassElementRepresentingThisPlaceholder(
            @Nullable resolved: KotlinClassElement?,
            @NonNull bounds: List<KotlinClassElement>
        ) = resolved ?: WildcardElement.findUpperType(bounds, bounds)
    }
}
