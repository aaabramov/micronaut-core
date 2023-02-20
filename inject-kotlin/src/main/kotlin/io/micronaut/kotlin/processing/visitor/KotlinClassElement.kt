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
import io.micronaut.context.annotation.BeanProperties
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils
import io.micronaut.inject.ast.utils.EnclosedElementsQuery
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.getClassDeclaration
import java.util.*
import java.util.function.Function
import java.util.stream.Stream
import kotlin.collections.ArrayList

open class KotlinClassElement(
    val kotlinType: KSType,
    protected val classDeclaration: KSClassDeclaration,
    private val annotationInfo: KSAnnotated,
    protected val elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    var resolvedTypeArguments: Map<String, ClassElement>?,
    visitorContext: KotlinVisitorContext,
    private val arrayDimensions: Int = 0,
    private val typeVariable: Boolean = false
) : AbstractKotlinElement<KSAnnotated>(annotationInfo, elementAnnotationMetadataFactory, visitorContext),
    ArrayableClassElement {

    constructor(
        ref: KSAnnotated,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        resolvedTypeArguments: Map<String, ClassElement>?,
        arrayDimensions: Int = 0,
        typeVariable: Boolean = false
    ) : this(
        getType(ref, visitorContext),
        ref.getClassDeclaration(visitorContext),
        ref,
        elementAnnotationMetadataFactory,
        resolvedTypeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    constructor(
        type: KSType,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        resolvedTypeArguments: Map<String, ClassElement>?,
        arrayDimensions: Int = 0,
        typeVariable: Boolean = false
    ) : this(
        type,
        type.declaration.getClassDeclaration(visitorContext),
        type.declaration.getClassDeclaration(visitorContext),
        elementAnnotationMetadataFactory,
        resolvedTypeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    val outerType: KSType? by lazy {
        val outerDecl = classDeclaration.parentDeclaration as? KSClassDeclaration
        outerDecl?.asType(kotlinType.arguments.subList(classDeclaration.typeParameters.size, kotlinType.arguments.size))
    }

    private val resolvedProperties: List<PropertyElement> by lazy {
        getBeanProperties(PropertyElementQuery.of(this))
    }
    private val internalDeclaredGenericPlaceholders: List<GenericPlaceholderElement> by lazy {
        kotlinType.declaration.typeParameters.map {
            resolveTypeParameter(it, emptyMap()) as GenericPlaceholderElement
        }.toMutableList()
    }
    private val internalFields: List<FieldElement> by lazy {
        super.getFields()
    }
    private val internalMethods: List<MethodElement> by lazy {
        super.getMethods()
    }
    private val enclosedElementsQuery = KotlinEnclosedElementsQuery()
    private val nativeProperties: List<PropertyElement> by lazy {
        val properties: MutableList<PropertyElement> = ArrayList()
        var clazz: KSClassDeclaration? = classDeclaration
        while (clazz != null) {
            clazz.getDeclaredProperties()
                .filter { !it.isPrivate() }
                .map {
                    KotlinPropertyElement(
                        this,
                        visitorContext.elementFactory.newClassElement(
                            it.type.resolve(),
                            elementAnnotationMetadataFactory
                        ),
                        it,
                        elementAnnotationMetadataFactory, visitorContext
                    )
                }
                .filter { !it.hasAnnotation(JvmField::class.java) }
                .forEach { properties.add(it) }
            val ksTypeReference = clazz.superTypes.firstOrNull()
            clazz = if (ksTypeReference is KSClassDeclaration) {
                ksTypeReference
            } else {
                null
            }
        }
        properties
    }
    private val internalCanonicalName: String by lazy {
        classDeclaration.qualifiedName!!.asString()
    }
    private val internalName: String by lazy {
        classDeclaration.getBinaryName(visitorContext.resolver, visitorContext)
    }
    private val resolvedInterfaces: Collection<ClassElement> by lazy {
        classDeclaration.superTypes.map { it.resolve() }
            .filter {
                it != visitorContext.resolver.builtIns.anyType
            }
            .filter {
                val declaration = it.declaration
                declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE
            }.map {
                newClassElement(it, typeArguments)
            }.toList()
    }
    private val resolvedSuperType: Optional<ClassElement> by lazy {
        val superType = classDeclaration.superTypes.firstOrNull {
            val resolved = it.resolve()
            if (resolved == visitorContext.resolver.builtIns.anyType) {
                false
            } else {
                val declaration = resolved.declaration
                declaration is KSClassDeclaration && declaration.classKind != ClassKind.INTERFACE
            }
        }
        Optional.ofNullable(superType)
            .map {
                newClassElement(it.resolve(), typeArguments)
            }
    }

    private val nt: KSAnnotated =
        if (annotationInfo is KSTypeArgument) annotationInfo else KSClassReference(classDeclaration)

    override fun getNativeType() = nt

    companion object Helper {
        fun getType(ref: KSAnnotated, visitorContext: KotlinVisitorContext): KSType {
            if (ref is KSType) {
                return ref
            } else if (ref is KSTypeReference) {
                return ref.resolve()
            } else if (ref is KSTypeParameter) {
                return ref.bounds.firstOrNull()?.resolve() ?: visitorContext.resolver.builtIns.anyType
            } else if (ref is KSClassDeclaration) {
                return ref.asStarProjectedType()
            } else if (ref is KSTypeArgument) {
                val ksType = ref.type?.resolve()
                if (ksType != null) {
                    return ksType
                } else {
                    throw IllegalArgumentException("Unresolvable type argument $ref")
                }
            } else if (ref is KSTypeAlias) {
                return ref.type.resolve()
            } else {
                throw IllegalArgumentException("Not a type $ref")
            }
        }
    }

    override fun getName() = internalName

    override fun getCanonicalName() = internalCanonicalName

    override fun getPackageName() = classDeclaration.packageName.asString()

    override fun isDeclaredNullable() = kotlinType.isMarkedNullable

    override fun isNullable() = kotlinType.isMarkedNullable

    override fun getSyntheticBeanProperties() = nativeProperties

    override fun getAccessibleStaticCreators(): List<MethodElement> {
        val staticCreators: MutableList<MethodElement> = mutableListOf()
        staticCreators.addAll(super.getAccessibleStaticCreators())
        return staticCreators.ifEmpty {
            val companion = classDeclaration.declarations
                .filter { it is KSClassDeclaration && it.isCompanionObject }
                .map { it as KSClassDeclaration }
                .map { newKotlinClassElement(it, emptyMap()) }
                .firstOrNull() ?: return emptyList()

            return companion.getEnclosedElements(
                ElementQuery.ALL_METHODS
                    .annotated {
                        it.hasStereotype(
                            Creator::class.java
                        )
                    }
                    .modifiers { it.isEmpty() || it.contains(ElementModifier.PUBLIC) }
                    .filter { method ->
                        method.returnType.isAssignable(this)
                    }
            )
        }
    }

    override fun getBeanProperties() = resolvedProperties

    override fun getDeclaredGenericPlaceholders() = internalDeclaredGenericPlaceholders

    override fun getFields() = internalFields

    override fun findField(name: String) = Optional.ofNullable(
        internalFields.firstOrNull { it.name == name }
    )

    override fun getMethods() = internalMethods

    override fun findMethod(name: String?) = Optional.ofNullable(
        internalMethods.firstOrNull { it.name == name }
    )

//    override fun withBoundGenericTypes(typeArguments: MutableList<out ClassElement>?): ClassElement {
//        if (typeArguments != null && typeArguments.size == kotlinType.declaration.typeParameters.size) {
//            val copy = copyThis()
//            copy.overrideBoundGenericTypes = typeArguments
//
//            val i = typeArguments.iterator()
//            copy.resolvedTypeArguments = kotlinType.declaration.typeParameters.associate {
//                it.name.asString() to i.next()
//            }.toMutableMap()
//            return copy
//        }
//        return this
//    }

//    override fun getBoundGenericTypes(): MutableList<out ClassElement> {
//        if (overrideBoundGenericTypes == null) {
//            val arguments = kotlinType.arguments
//            if (arguments.isEmpty()) {
//                return mutableListOf()
//            } else {
//                overrideBoundGenericTypes = arguments.map { arg ->
//                    when(arg.variance) {
//                        Variance.STAR, Variance.COVARIANT, Variance.CONTRAVARIANT -> KotlinWildcardElement( // example List<*>
//                            resolveUpperBounds(arg),
//                            resolveLowerBounds(arg),
//                            elementAnnotationMetadataFactory, visitorContext
//                        )
//                        else -> newClassElement( // other cases
//                            arg,
//                            emptyMap(),
//                            false
//                        )
//                    }
//                }.toMutableList()
//            }
//        }
//        return overrideBoundGenericTypes!!
//    }

//    private fun populateGenericInfo(
//        classDeclaration: KSClassDeclaration,
//        data: MutableMap<String, Map<String, KSType>>,
//        boundMirrors: Map<String, KSType>?
//    ) {
//        classDeclaration.superTypes.forEach {
//            val superType = it.resolve()
//            if (superType != visitorContext.resolver.builtIns.anyType) {
//                val declaration = superType.declaration
//                val name = declaration.qualifiedName?.asString()
//                val binaryName = declaration.getBinaryName(visitorContext.resolver, visitorContext)
//                if (name != null && !data.containsKey(name)) {
//                    val typeParameters = declaration.typeParameters
//                    if (typeParameters.isEmpty()) {
//                        data[binaryName] = emptyMap()
//                    } else {
//                        val ksTypeArguments = superType.arguments
//                        if (typeParameters.size == ksTypeArguments.size) {
//                            val resolved = LinkedHashMap<String, KSType>()
//                            var i = 0
//                            typeParameters.forEach { typeParameter ->
//                                val parameterName = typeParameter.name.asString()
//                                val typeArgument = ksTypeArguments[i]
//                                val argumentType = typeArgument.type?.resolve()
//                                val argumentName = argumentType?.declaration?.simpleName?.asString()
//                                val bound = if (argumentName != null ) boundMirrors?.get(argumentName) else null
//                                if (bound != null) {
//                                    resolved[parameterName] = bound
//                                } else {
//                                    resolved[parameterName] = argumentType ?: typeParameter.bounds.firstOrNull()?.resolve()
//                                            ?: visitorContext.resolver.builtIns.anyType
//                                }
//                                i++
//                            }
//                            data[binaryName] = resolved
//                        }
//                    }
//                    if (declaration is KSClassDeclaration) {
//                        val newBounds = data[binaryName]
//                        populateGenericInfo(
//                            declaration,
//                            data,
//                            newBounds
//                        )
//                    }
//                }
//            }
//
//        }
//    }

//    private fun getBoundTypeMirrors(): Map<String, KSType> {
//        val typeParameters: List<KSTypeArgument> = kotlinType.arguments
//        val parameterIterator = classDeclaration.typeParameters.iterator()
//        val tpi = typeParameters.iterator()
//        val map: MutableMap<String, KSType> = LinkedHashMap()
//        while (tpi.hasNext() && parameterIterator.hasNext()) {
//            val tpe = tpi.next()
//            val parameter = parameterIterator.next()
//            val resolvedType = tpe.type?.resolve()
//            if (resolvedType != null) {
//                map[parameter.name.asString()] = resolvedType
//            } else {
//                map[parameter.name.asString()] = visitorContext.resolver.builtIns.anyType
//            }
//        }
//        return Collections.unmodifiableMap(map)
//    }

//    private fun resolveLowerBounds(arg: KSTypeArgument): List<KotlinClassElement?> {
//        return if (arg.variance == Variance.CONTRAVARIANT) {
//            listOf(
//                newKotlinClassElement(arg.type?.resolve()!!, emptyMap(), false) as KotlinClassElement
//            )
//        } else {
//            return emptyList()
//        }
//    }
//
//    private fun resolveUpperBounds(arg: KSTypeArgument): List<KotlinClassElement?> {
//        return if (arg.variance == Variance.COVARIANT) {
//            listOf(
//                newClassElement(arg.type?.resolve()!!, emptyMap(), false) as KotlinClassElement
//            )
//        } else {
//            val objectType = visitorContext.resolver.getClassDeclarationByName(Object::class.java.name)!!
//            listOf(
//                newClassElement(objectType.asStarProjectedType(), emptyMap(), false) as KotlinClassElement
//            )
//        }
//    }

    override fun getBeanProperties(propertyElementQuery: PropertyElementQuery): MutableList<PropertyElement> {
        val customReaderPropertyNameResolver =
            Function<MethodElement, Optional<String>> { Optional.empty() }
        val customWriterPropertyNameResolver =
            Function<MethodElement, Optional<String>> { Optional.empty() }
        val accessKinds = propertyElementQuery.accessKinds
        val fieldAccess =
            accessKinds.contains(BeanProperties.AccessKind.FIELD) && !propertyElementQuery.accessKinds.contains(
                BeanProperties.AccessKind.METHOD
            )
        if (fieldAccess) {
            // all kotlin fields are private
            return mutableListOf()
        }

        val eq = ElementQuery.of(PropertyElement::class.java)
            .named { n -> !propertyElementQuery.excludes.contains(n) }
            .named { n -> propertyElementQuery.includes.isEmpty() || propertyElementQuery.includes.contains(n) }
            .modifiers {
                val visibility = propertyElementQuery.visibility
                if (visibility == BeanProperties.Visibility.PUBLIC) {
                    it.contains(ElementModifier.PUBLIC)
                } else {
                    !it.contains(ElementModifier.PRIVATE)
                }
            }.annotated { prop ->
                if (prop.hasAnnotation(JvmField::class.java)) {
                    false
                } else {
                    val excludedAnnotations = propertyElementQuery.excludedAnnotations
                    excludedAnnotations.isEmpty() || !excludedAnnotations.any { prop.hasAnnotation(it) }
                }
            }

        val allProperties: MutableList<PropertyElement> = mutableListOf()
        // unfortunate hack since these are not excluded?
        if (hasDeclaredStereotype(ConfigurationReader::class.java)) {
            val configurationBuilderQuery = ElementQuery.of(PropertyElement::class.java)
                .annotated { it.hasDeclaredAnnotation(ConfigurationBuilder::class.java) }
                .onlyInstance()
            val configBuilderProps = enclosedElementsQuery.getEnclosedElements(this, configurationBuilderQuery)
            allProperties.addAll(configBuilderProps)
        }

        allProperties.addAll(enclosedElementsQuery.getEnclosedElements(this, eq))
        val propertyNames = allProperties.map { it.name }.toSet()

        val resolvedProperties: MutableList<PropertyElement> = mutableListOf()
        val methodProperties = AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
            this,
            {
                getEnclosedElements(
                    ElementQuery.ALL_METHODS
                )
            },
            {
                emptyList()
            },
            false, propertyNames,
            customReaderPropertyNameResolver,
            customWriterPropertyNameResolver,
            { value: AstBeanPropertiesUtils.BeanPropertyData ->
                if (!value.isExcluded) {
                    this.mapToPropertyElement(
                        value
                    )
                } else {
                    null
                }
            })
        resolvedProperties.addAll(methodProperties)
        resolvedProperties.addAll(allProperties)
        return resolvedProperties
    }

    private fun mapToPropertyElement(value: AstBeanPropertiesUtils.BeanPropertyData) =
        KotlinPropertyElement(
            this@KotlinClassElement,
            value.type,
            value.propertyName,
            value.field,
            value.getter,
            value.setter,
            elementAnnotationMetadataFactory,
            visitorContext,
            value.isExcluded
        )

    @OptIn(KspExperimental::class)
    override fun getSimpleName(): String {
        var parentDeclaration = classDeclaration.parentDeclaration
        return if (parentDeclaration == null) {
            val qualifiedName = classDeclaration.qualifiedName
            if (qualifiedName != null) {
                visitorContext.resolver.mapKotlinNameToJava(qualifiedName)?.getShortName()
                    ?: classDeclaration.simpleName.asString()
            } else
                classDeclaration.simpleName.asString()
        } else {
            val builder = StringBuilder(classDeclaration.simpleName.asString())
            while (parentDeclaration != null) {
                builder.insert(0, '$')
                    .insert(0, parentDeclaration.simpleName.asString())
                parentDeclaration = parentDeclaration.parentDeclaration
            }
            builder.toString()
        }
    }

    override fun getSuperType() = resolvedSuperType

    override fun getInterfaces() = resolvedInterfaces

    override fun isStatic() = if (isInner) {
        // inner classes in Kotlin are by default static unless
        // the 'inner' keyword is used
        !classDeclaration.modifiers.contains(Modifier.INNER)
    } else {
        super<AbstractKotlinElement>.isStatic()
    }

    override fun isInterface() = classDeclaration.classKind == ClassKind.INTERFACE

    override fun isTypeVariable() = typeVariable

    @OptIn(KspExperimental::class)
    override fun isAssignable(type: String): Boolean {
        var ksType = visitorContext.resolver.getClassDeclarationByName(type)?.asStarProjectedType()
        if (ksType != null) {
            if (ksType.isAssignableFrom(kotlinType)) {
                return true
            }
            val kotlinName = visitorContext.resolver.mapJavaNameToKotlin(
                visitorContext.resolver.getKSNameFromString(type)
            )
            if (kotlinName != null) {
                ksType = visitorContext.resolver.getKotlinClassByName(kotlinName)?.asStarProjectedType()
                if (ksType != null && kotlinType.starProjection().isAssignableFrom(ksType)) {
                    return true
                }
            }
            return false
        }
        return false
    }

    override fun isAssignable(type: ClassElement): Boolean {
        if (type is KotlinClassElement) {
            return type.kotlinType.isAssignableFrom(kotlinType)
        }
        return super.isAssignable(type)
    }

    override fun copyThis() = KotlinClassElement(
        kotlinType,
        classDeclaration,
        annotationInfo,
        elementAnnotationMetadataFactory,
        resolvedTypeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    override fun withTypeArguments(typeArguments: Map<String, ClassElement>) = KotlinClassElement(
        kotlinType,
        classDeclaration,
        annotationInfo,
        elementAnnotationMetadataFactory,
        typeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    @NonNull
    override fun withTypeArguments(@NonNull typeArguments: Collection<ClassElement>): ClassElement? {
        if (getTypeArguments() == typeArguments) {
            return this
        }
        if (typeArguments.isEmpty()) {
            return withTypeArguments(emptyMap())
        }
        val boundByName: MutableMap<String, ClassElement> = LinkedHashMap()
        val keys = getTypeArguments().keys
        val variableNames: Iterator<String> = keys.iterator()
        val args = typeArguments.iterator()
        while (variableNames.hasNext() && args.hasNext()) {
            var next = args.next()
            val nativeType = next.nativeType
            if (nativeType is Class<*>) {
                next = visitorContext.getClassElement(nativeType).orElse(next)
            }
            boundByName[variableNames.next()] = next
        }
        return withTypeArguments(boundByName)
    }

    override fun isAbstract(): Boolean = classDeclaration.isAbstract()

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as ClassElement

    override fun isArray() = arrayDimensions > 0

    override fun getArrayDimensions() = arrayDimensions

    override fun withArrayDimensions(arrayDimensions: Int) = KotlinClassElement(
        kotlinType,
        classDeclaration,
        annotationInfo,
        elementAnnotationMetadataFactory,
        resolvedTypeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    override fun isInner() = outerType != null

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        val primaryConstructor = super.getPrimaryConstructor()
        return if (primaryConstructor.isPresent) {
            primaryConstructor
        } else {
            Optional.ofNullable(classDeclaration.primaryConstructor)
                .filter { !it.isPrivate() }
                .map {
                    visitorContext.elementFactory.newConstructorElement(
                        this,
                        it,
                        elementAnnotationMetadataFactory
                    )
                }
        }
    }

    override fun getDefaultConstructor(): Optional<MethodElement> {
        val defaultConstructor = super.getDefaultConstructor()
        return if (defaultConstructor.isPresent) {
            defaultConstructor
        } else {
            Optional.ofNullable(classDeclaration.primaryConstructor)
                .filter { !it.isPrivate() && it.parameters.isEmpty() }
                .map {
                    visitorContext.elementFactory.newConstructorElement(
                        this,
                        it,
                        elementAnnotationMetadataFactory
                    )
                }
        }
    }

    override fun getTypeArguments(): Map<String, ClassElement> {
        if (resolvedTypeArguments == null) {
            resolvedTypeArguments = resolveTypeArguments(classDeclaration, emptyMap())
//            val typeArguments = mutableMapOf<String, ClassElement>()
//            val elementFactory = visitorContext.elementFactory
//            val typeParameters = kotlinType.declaration.typeParameters
//            if (kotlinType.arguments.isEmpty()) {
//                typeParameters.forEach {
//                    typeArguments[it.name.asString()] = KotlinGenericPlaceholderElement(it, annotationMetadataFactory, visitorContext)
//                }
//            } else {
//                kotlinType.arguments.forEachIndexed { i, argument ->
//                    val typeElement = elementFactory.newClassElement(
//                        argument,
//                        annotationMetadataFactory,
//                        false
//                    )
//                    typeArguments[typeParameters[i].name.asString()] = typeElement
//                }
//            }
//            resolvedTypeArguments = typeArguments
        }
        return resolvedTypeArguments!!
    }

//    override fun getAllTypeArguments(): Map<String, Map<String, ClassElement>> {
//        val genericInfo = getGenericTypeInfo()
//        return genericInfo.mapValues { entry ->
//            entry.value.mapValues { data ->
//                visitorContext.elementFactory.newClassElement(data.value, elementAnnotationMetadataFactory, false)
//            }
//        }
//    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            return Optional.of(
                visitorContext.elementFactory.newClassElement(
                    outerType!!,
                    visitorContext.elementAnnotationMetadataFactory
                )
            )
        }
        return Optional.empty()
    }

    override fun <T : Element> getEnclosedElements(query: ElementQuery<T>): List<T> =
        enclosedElementsQuery.getEnclosedElements(this, query)

    private inner class KotlinEnclosedElementsQuery : EnclosedElementsQuery<KSClassDeclaration, KSNode>() {

        override fun getExcludedNativeElements(result: ElementQuery.Result<*>): Set<KSNode> {
            if (result.isExcludePropertyElements) {
                val excludeElements: MutableSet<KSNode> = HashSet()
                for (excludePropertyElement in beanProperties) {
                    excludePropertyElement.readMethod.ifPresent { methodElement: MethodElement ->
                        excludeElements.add(
                            methodElement.nativeType as KSNode
                        )
                    }
                    excludePropertyElement.writeMethod.ifPresent { methodElement: MethodElement ->
                        excludeElements.add(
                            methodElement.nativeType as KSNode
                        )
                    }
                    excludePropertyElement.field.ifPresent { fieldElement: FieldElement ->
                        excludeElements.add(
                            fieldElement.nativeType as KSNode
                        )
                    }
                }
                return excludeElements
            }
            return emptySet()
        }

        override fun getCacheKey(element: KSNode): KSNode {
            return when (element) {
                is KSFunctionDeclaration -> KSFunctionReference(element)
                is KSPropertyDeclaration -> KSPropertyReference(element)
                is KSClassDeclaration -> KSClassReference(element)
                is KSValueParameter -> KSValueParameterReference(element)
                is KSPropertyGetter -> KSPropertyGetterReference(element)
                is KSPropertySetter -> KSPropertySetterReference(element)
                else -> element
            }
        }

        override fun getSuperClass(classNode: KSClassDeclaration): KSClassDeclaration? {
            val superTypes = classNode.superTypes
            for (superclass in superTypes) {
                val resolved = superclass.resolve()
                val declaration = resolved.declaration
                if (declaration is KSClassDeclaration) {
                    if (declaration.classKind == ClassKind.CLASS && declaration.qualifiedName?.asString() != Any::class.qualifiedName) {
                        return declaration
                    }
                }
            }
            return null
        }

        override fun getInterfaces(classDeclaration: KSClassDeclaration): Collection<KSClassDeclaration> {
            val superTypes = classDeclaration.superTypes
            val result: MutableCollection<KSClassDeclaration> = ArrayList()
            for (superclass in superTypes) {
                val resolved = superclass.resolve()
                val declaration = resolved.declaration
                if (declaration is KSClassDeclaration) {
                    if (declaration.classKind == ClassKind.INTERFACE) {
                        result.add(declaration)
                    }
                }
            }
            return result
        }

        override fun getEnclosedElements(
            classNode: KSClassDeclaration,
            result: ElementQuery.Result<*>
        ): List<KSNode> {
            val elementType: Class<*> = result.elementType
            return getEnclosedElements(classNode, result, elementType)
        }

        private fun getEnclosedElements(
            classNode: KSClassDeclaration,
            result: ElementQuery.Result<*>,
            elementType: Class<*>
        ): List<KSNode> {
            return when (elementType) {
                MemberElement::class.java -> {
                    Stream.concat(
                        getEnclosedElements(classNode, result, FieldElement::class.java).stream(),
                        getEnclosedElements(classNode, result, MethodElement::class.java).stream()
                    ).toList()
                }

                MethodElement::class.java -> {
                    classNode.getDeclaredFunctions()
                        .filter { func: KSFunctionDeclaration ->
                            !func.isConstructor() &&
                                    func.origin != Origin.SYNTHETIC &&
                                    // this is a hack but no other way it seems
                                    !listOf("hashCode", "toString", "equals").contains(func.simpleName.asString())
                        }
                        .toList()
                }

                FieldElement::class.java -> {
                    classNode.getDeclaredProperties()
                        .filter {
                            it.hasBackingField &&
                                    it.origin != Origin.SYNTHETIC
                        }
                        .toList()
                }

                PropertyElement::class.java -> {
                    classNode.getDeclaredProperties().toList()
                }

                ConstructorElement::class.java -> {
                    classNode.getConstructors().toList()
                }

                ClassElement::class.java -> {
                    classNode.declarations.filter {
                        it is KSClassDeclaration
                    }.toList()
                }

                else -> {
                    throw java.lang.IllegalStateException("Unknown result type: $elementType")
                }
            }
        }

        override fun excludeClass(classNode: KSClassDeclaration): Boolean {
            val t = classNode.asStarProjectedType()
            val builtIns = visitorContext.resolver.builtIns
            return t == builtIns.anyType ||
                    t == builtIns.nothingType ||
                    t == builtIns.unitType ||
                    classNode.qualifiedName.toString() == Enum::class.java.name
        }

        override fun toAstElement(
            nativeType: KSNode,
            elementType: Class<*>
        ): Element {
            var ee = nativeType
            if (ee is KSAnnotatedReference) {
                ee = ee.node
            }
            val elementFactory: KotlinElementFactory = visitorContext.elementFactory
            return when (ee) {
                is KSFunctionDeclaration -> {
                    if (ee.isConstructor()) {
                        return elementFactory.newConstructorElement(
                            this@KotlinClassElement,
                            ee,
                            elementAnnotationMetadataFactory
                        )
                    } else {
                        return elementFactory.newMethodElement(
                            this@KotlinClassElement,
                            ee,
                            elementAnnotationMetadataFactory
                        )
                    }
                }

                is KSPropertyDeclaration -> {
                    if (elementType == PropertyElement::class.java) {
                        val prop = KotlinPropertyElement(
                            this@KotlinClassElement,
                            visitorContext.elementFactory.newClassElement(
                                ee.type.resolve(),
                                elementAnnotationMetadataFactory
                            ),
                            ee,
                            elementAnnotationMetadataFactory, visitorContext
                        )
                        if (!prop.hasAnnotation(JvmField::class.java)) {
                            return prop
                        } else {
                            return elementFactory.newFieldElement(
                                this@KotlinClassElement,
                                ee,
                                elementAnnotationMetadataFactory
                            )
                        }
                    } else {
                        return elementFactory.newFieldElement(
                            this@KotlinClassElement,
                            ee,
                            elementAnnotationMetadataFactory
                        )
                    }
                }

                is KSType -> elementFactory.newClassElement(
                    ee,
                    elementAnnotationMetadataFactory
                )

                is KSClassDeclaration -> newKotlinClassElement(
                    ee,
                    emptyMap()
                )

                else -> throw ProcessingException(this@KotlinClassElement, "Unknown element: $ee")
            }
        }
    }


}
