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
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory

class KotlinConstructorElement(method: KSFunctionDeclaration,
                               declaringType: ClassElement,
                               elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                               visitorContext: KotlinVisitorContext,
                               returnType: ClassElement
): ConstructorElement, KotlinMethodElement(method, declaringType, returnType, elementAnnotationMetadataFactory, visitorContext) {

    init {
        if (method.closestClassDeclaration()?.modifiers?.contains(Modifier.DATA) == true &&
            declaringType.hasDeclaredStereotype(ConfigurationReader::class.java)) {
            annotate(ConfigurationInject::class.java)
        }
    }

    override fun overrides(overridden: MethodElement): Boolean {
        return false
    }

    override fun hides(memberElement: MemberElement?): Boolean {
        return false
    }

    override fun getName() = "<init>"

    override fun getReturnType(): ClassElement = declaringType

}
