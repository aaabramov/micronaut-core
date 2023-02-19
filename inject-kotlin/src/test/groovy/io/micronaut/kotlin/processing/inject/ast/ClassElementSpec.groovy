package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.WildcardElement

class ClassElementSpec extends AbstractKotlinCompilerSpec {

    void "test class element"() {
        expect:
        buildClassElement('ast.test.Test', '''
package ast.test

import java.lang.IllegalStateException
import kotlin.jvm.Throws


class Test(
    val publicConstructorReadOnly : String,
    private val privateConstructorReadOnly : String,
    protected val protectedConstructorReadOnly : Boolean
) : Parent(), One, Two {

    val publicReadOnlyProp : Boolean = true
    protected val protectedReadOnlyProp : Boolean? = true
    private val privateReadOnlyProp : Boolean? = true
    var publicReadWriteProp : Boolean = true
    protected var protectedReadWriteProp : String? = "ok"
    private var privateReadWriteProp : String = "ok"
    private var conventionProp : String = "ok"

    private fun privateFunc(name : String) : String {
        return "ok"
    }

    open fun openFunc(name : String) : String {
        return "ok"
    }

    protected fun protectedFunc(name : String) : String {
        return "ok"
    }

    @Throws(IllegalStateException::class)
    override fun publicFunc(name : String) : String {
        return "ok"
    }

    suspend fun suspendFunc(name : String) : String {
        return "ok"
    }

    fun getConventionProp() : String {
        return conventionProp
    }

    fun setConventionProp(name : String) {
        this.conventionProp = name
    }


    companion object Helper {
        fun publicStatic() : String {
            return "ok"
        }

        private fun privateStatic() : String {
            return "ok"
        }
    }

    inner class InnerClass1

    class InnerClass2
}

open class Parent : Three {
    open fun publicFunc(name : String) : String {
        return "ok"
    }

    fun parentFunc() : Boolean {
        return true
    }

    companion object ParentHelper {
        fun publicStatic() : String {
            return "ok"
        }
    }
}

interface One
interface Two
interface Three
''') { ClassElement classElement ->
            List<ConstructorElement> constructorElements = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)
            List<ClassElement> allInnerClasses = classElement.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES)
            List<ClassElement> declaredInnerClasses = classElement.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES.onlyDeclared())
            List<PropertyElement> propertyElements = classElement.getBeanProperties()
            List<PropertyElement> syntheticProperties = classElement.getSyntheticBeanProperties()
            List<MethodElement> methodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
            List<MethodElement> declaredMethodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
            List<MethodElement> includeOverridden = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeOverriddenMethods())
            Map<String, MethodElement> methodMap = methodElements.collectEntries {
                [it.name, it]
            }
            Map<String, MethodElement> declaredMethodMap = declaredMethodElements.collectEntries {
                [it.name, it]
            }
            Map<String, PropertyElement> propMap = propertyElements.collectEntries {
                [it.name, it]
            }
            Map<String, PropertyElement> synthPropMap = syntheticProperties.collectEntries {
                [it.name, it]
            }
            Map<String, ClassElement> declaredInnerMap = declaredInnerClasses.collectEntries {
                [it.simpleName, it]
            }
            Map<String, ClassElement> innerMap = allInnerClasses.collectEntries {
                [it.simpleName, it]
            }

            def overridden = includeOverridden.find { it.declaringType.simpleName == 'Parent' && it.name == 'publicFunc' }

            assert classElement != null
            assert classElement.interfaces*.simpleName as Set == ['One', "Two"] as Set
            assert methodElements != null
            assert !classElement.isAbstract()
            assert classElement.name == 'ast.test.Test'
            assert !classElement.isPrivate()
            assert classElement.isPublic()
            assert classElement.modifiers == [ElementModifier.FINAL, ElementModifier.PUBLIC] as Set
            assert constructorElements.size() == 1
            assert constructorElements[0].parameters.size() == 3
            assert classElement.superType.isPresent()
            assert classElement.superType.get().simpleName == 'Parent'
            assert !classElement.superType.get().getSuperType().isPresent()
            assert propertyElements.size() == 7
            assert propMap.size() == 7
            assert synthPropMap.size() == 6
            assert methodElements.size() == 8
            assert includeOverridden.size() == 9
            assert declaredMethodElements.size() == 7
            assert propMap.keySet() == ['conventionProp', 'publicReadOnlyProp', 'protectedReadOnlyProp', 'publicReadWriteProp', 'protectedReadWriteProp', 'publicConstructorReadOnly', 'protectedConstructorReadOnly'] as Set
            assert synthPropMap.keySet() == ['publicReadOnlyProp', 'protectedReadOnlyProp', 'publicReadWriteProp', 'protectedReadWriteProp', 'publicConstructorReadOnly', 'protectedConstructorReadOnly'] as Set
            // inner classes
            assert allInnerClasses.size() == 4
            assert declaredInnerClasses.size() == 3
            assert !declaredInnerMap['Test$InnerClass1'].isStatic()
            assert declaredInnerMap['Test$InnerClass2'].isStatic()
            assert declaredInnerMap['Test$InnerClass1'].isPublic()
            assert declaredInnerMap['Test$InnerClass2'].isPublic()

            // read-only public
            assert propMap['publicReadOnlyProp'].isReadOnly()
            assert !propMap['publicReadOnlyProp'].isWriteOnly()
            assert propMap['publicReadOnlyProp'].isPublic()
            assert propMap['publicReadOnlyProp'].readMethod.isPresent()
            assert propMap['publicReadOnlyProp'].readMethod.get().isSynthetic()
            assert !propMap['publicReadOnlyProp'].writeMethod.isPresent()
            // read/write public property
            assert !propMap['publicReadWriteProp'].isReadOnly()
            assert !propMap['publicReadWriteProp'].isWriteOnly()
            assert propMap['publicReadWriteProp'].isPublic()
            assert propMap['publicReadWriteProp'].readMethod.isPresent()
            assert propMap['publicReadWriteProp'].readMethod.get().isSynthetic()
            assert propMap['publicReadWriteProp'].writeMethod.isPresent()
            assert propMap['publicReadWriteProp'].writeMethod.get().isSynthetic()
            // convention prop
            assert !propMap['conventionProp'].isReadOnly()
            assert !propMap['conventionProp'].isWriteOnly()
            assert propMap['conventionProp'].isPublic()
            assert propMap['conventionProp'].readMethod.isPresent()
            assert !propMap['conventionProp'].readMethod.get().isSynthetic()
            assert propMap['conventionProp'].writeMethod.isPresent()
            assert !propMap['conventionProp'].writeMethod.get().isSynthetic()

            // methods
            assert methodMap.keySet() == ['publicFunc',  'parentFunc', 'openFunc', 'privateFunc', 'protectedFunc', 'suspendFunc', 'getConventionProp', 'setConventionProp'] as Set
            assert declaredMethodMap.keySet()  == ['publicFunc', 'openFunc', 'privateFunc', 'protectedFunc', 'suspendFunc', 'getConventionProp', 'setConventionProp'] as Set
            assert methodMap['suspendFunc'].isSuspend()
            assert methodMap['suspendFunc'].returnType.name == String.name
            assert methodMap['suspendFunc'].parameters.size() == 1
            assert methodMap['suspendFunc'].suspendParameters.size() == 2
            assert !methodMap['openFunc'].isFinal()
            assert !methodMap['publicFunc'].isPackagePrivate()
            assert !methodMap['publicFunc'].isPrivate()
            assert !methodMap['publicFunc'].isStatic()
            assert !methodMap['publicFunc'].isReflectionRequired()
            assert methodMap['publicFunc'].hasParameters()
            assert methodMap['publicFunc'].thrownTypes.size() == 1
            assert methodMap['publicFunc'].thrownTypes[0].name == IllegalStateException.name
            assert methodMap['publicFunc'].isPublic()
            assert methodMap['publicFunc'].owningType.name == 'ast.test.Test'
            assert methodMap['publicFunc'].declaringType.name == 'ast.test.Test'
            assert !methodMap['publicFunc'].isFinal() // should be final? But apparently not
            assert overridden != null
            assert methodMap['publicFunc'].overrides(overridden)
        }
    }

    void "test class element generics"() {
        expect:
        buildClassElement('ast.test.Test', '''
package ast.test

/**
* Class docs
*
* @param constructorProp construct prop
*/
class Test(
    val constructorProp : String) : Parent<String>(constructorProp), One<String> {
    /**
     * Property doc
     */
    val publicReadOnlyProp : Boolean = true
    override val size: Int = 10
    override fun get(index: Int): String {
        return "ok"
    }

    open fun openFunc(name : String) : String {
        return "ok"
    }

    /**
    * Method doc
    * @param name Param name
    */
    override fun publicFunc(name : String) : String {
        return "ok"
    }
}

open abstract class Parent<T : CharSequence>(val parentConstructorProp : T) : AbstractMutableList<T>() {

    var parentProp : T = parentConstructorProp
    private var conventionProp : T = parentConstructorProp

    fun getConventionProp() : T {
        return conventionProp
    }
    override fun add(index: Int, element: T){
        TODO("Not yet implemented")
    }
    override fun removeAt(index: Int): T{
        TODO("Not yet implemented")
    }
    override fun set(index: Int, element: T): T{
        TODO("Not yet implemented")
    }
    fun setConventionProp(name : T) {
        this.conventionProp = name
    }

    open fun publicFunc(name : T) : T {
        TODO("not yet implemented")
    }

    fun parentFunc(name :  T) : T {
        TODO("not yet implemented")
    }

    suspend fun suspendFunc(name : T) : T {
        TODO("not yet implemented")
    }
}

interface One<E>
interface Two
interface Three
''') { ClassElement classElement ->
            List<ConstructorElement> constructorElements = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)
            List<PropertyElement> propertyElements = classElement.getBeanProperties()
            List<PropertyElement> syntheticProperties = classElement.getSyntheticBeanProperties()
            List<MethodElement> methodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
            Map<String, MethodElement> methodMap = methodElements.collectEntries {
                [it.name, it]
            }
            Map<String, PropertyElement> propMap = propertyElements.collectEntries {
                [it.name, it]
            }

            assert classElement.documentation.isPresent()
            assert methodMap['add'].parameters[1].genericType.simpleName == 'String'
            assert methodMap['add'].parameters[1].type.simpleName == 'CharSequence'
            assert methodMap['iterator'].returnType.firstTypeArgument.get().simpleName == 'Object'
            assert methodMap['iterator'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
            assert methodMap['stream'].returnType.firstTypeArgument.get().simpleName == 'Object'
            assert methodMap['stream'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
            assert propMap['conventionProp'].type.simpleName == 'String'
            assert propMap['conventionProp'].genericType.simpleName == 'String'
            assert propMap['conventionProp'].genericType.simpleName == 'String'
            assert propMap['conventionProp'].readMethod.get().returnType.simpleName == 'CharSequence'
            assert propMap['conventionProp'].readMethod.get().genericReturnType.simpleName == 'String'
            assert propMap['conventionProp'].writeMethod.get().parameters[0].type.simpleName == 'CharSequence'
            assert propMap['conventionProp'].writeMethod.get().parameters[0].genericType.simpleName == 'String'
            assert propMap['parentConstructorProp'].type.simpleName == 'CharSequence'
            assert propMap['parentConstructorProp'].genericType.simpleName == 'String'
            assert methodMap['publicFunc'].documentation.isPresent()
            assert methodMap['parentFunc'].returnType.simpleName == 'CharSequence'
            assert methodMap['parentFunc'].genericReturnType.simpleName == 'String'
            assert methodMap['parentFunc'].parameters[0].type.simpleName == 'CharSequence'
            assert methodMap['parentFunc'].parameters[0].genericType.simpleName == 'String'
        }
    }

    void "test annotation metadata present on deep type parameters for field"() {
        ClassElement ce = buildClassElement('test.Test', '''
package test;
import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.List;

class Test {
    deepList: List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>> = null;
}
''')
        expect:
            def field = ce.getFields().find { it.name == "deepList"}
            def fieldType = field.getGenericType()

            fieldType.getAnnotationMetadata().getAnnotationNames().size() == 0

            assertListGenericArgument(fieldType, { ClassElement listArg1 ->
                assert listArg1.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.Size$List']
                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
                    assert listArg2.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotEmpty$List']
                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
                        assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotNull$List']
                    })
                })
            })

            def level1 = fieldType.getTypeArguments()["E"]
            level1.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.Size$List']
            def level2 = level1.getTypeArguments()["E"]
            level2.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotEmpty$List']
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotNull$List']
    }

//    void "test annotation metadata present on deep type parameters for method"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import io.micronaut.core.annotation.*;
//import javax.validation.constraints.*;
//import java.util.List;
//
//class Test {
//    List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>> deepList() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("deepList")).get()
//            def theType = method.getGenericReturnType()
//
//            theType.getAnnotationMetadata().getAnnotationNames().size() == 0
//
//            assertListGenericArgument(theType, { ClassElement listArg1 ->
//                assert listArg1.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.Size$List']
//                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
//                    assert listArg2.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotEmpty$List']
//                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
//                        assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotNull$List']
//                    })
//                })
//            })
//
//            def level1 = theType.getTypeArguments()["E"]
//            level1.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.Size$List']
//            def level2 = level1.getTypeArguments()["E"]
//            level2.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotEmpty$List']
//            def level3 = level2.getTypeArguments()["E"]
//            level3.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotNull$List']
//    }
//
//    void "test type annotations on a method and a field"() {
//        ClassElement ce = buildClassElement('''
//package test;
//
//class Test {
//    @io.micronaut.visitors.TypeUseRuntimeAnn
//    @io.micronaut.visitors.TypeUseClassAnn
//    String myField;
//
//    @io.micronaut.visitors.TypeUseRuntimeAnn
//    @io.micronaut.visitors.TypeUseClassAnn
//    String myMethod() {
//        return null;
//    }
//}
//''')
//        expect:
//            def field = ce.findField("myField").get()
//            def method = ce.findMethod("myMethod").get()
//
//            // Type annotations shouldn't appear on the field
//            field.getAnnotationMetadata().getAnnotationNames().asList() == []
//            field.getType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//            field.getGenericType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//            // Type annotations shouldn't appear on the method
//            method.getAnnotationMetadata().getAnnotationNames().asList() == []
//            method.getReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//            method.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//    }
//
//    void "test type annotations on a method and a field 2"() {
//        ClassElement ce = buildClassElement('''
//package test;
//
//class Test {
//    @io.micronaut.visitors.TypeFieldRuntimeAnn
//    @io.micronaut.visitors.TypeUseRuntimeAnn
//    @io.micronaut.visitors.TypeUseClassAnn
//    String myField;
//
//    @io.micronaut.visitors.TypeMethodRuntimeAnn
//    @io.micronaut.visitors.TypeUseRuntimeAnn
//    @io.micronaut.visitors.TypeUseClassAnn
//    String myMethod() {
//        return null;
//    }
//}
//''')
//        expect:
//            def field = ce.findField("myField").get()
//            def method = ce.findMethod("myMethod").get()
//
//            // Type annotations shouldn't appear on the field
//            field.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeFieldRuntimeAnn']
//            field.getType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//            field.getGenericType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//            // Type annotations shouldn't appear on the method
//            method.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeMethodRuntimeAnn']
//            method.getReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//            method.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//    }
//
//    void "test recursive generic type parameter"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//final class TrackedSortedSet<T extends java.lang.Comparable<? super T>> {
//}
//
//''')
//        expect:
//            def typeArguments = ce.getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "java.lang.Comparable"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "java.lang.Comparable"
//            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
//            def nextNextTypeArgument = nextNextTypeArguments.get("T")
//            nextNextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test annotation metadata present on deep type parameters for method 2"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import io.micronaut.core.annotation.*;
//import javax.validation.constraints.*;
//import java.util.List;
//
//class Test {
//    List<List<List<@io.micronaut.visitors.TypeUseRuntimeAnn @io.micronaut.visitors.TypeUseClassAnn String>>> deepList() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("deepList")).get()
//            def theType = method.getGenericReturnType()
//
//            theType.getAnnotationMetadata().getAnnotationNames().size() == 0
//
//            assertListGenericArgument(theType, { ClassElement listArg1 ->
//                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
//                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
//                        assert listArg3.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn']
//                    })
//                })
//            })
//
//            def level1 = theType.getTypeArguments()["E"]
//            def level2 = level1.getTypeArguments()["E"]
//            def level3 = level2.getTypeArguments()["E"]
//            level3.getAnnotationMetadata().getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn', 'io.micronaut.visitors.TypeUseClassAnn' ]
//    }
//
//    void "test annotations on recursive generic type parameter 1"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//final class TrackedSortedSet<@io.micronaut.visitors.TypeUseRuntimeAnn T extends java.lang.Comparable<? super T>> {
//}
//
//''')
//        expect:
//            def typeArguments = ce.getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "java.lang.Comparable"
//            typeArgument.getAnnotationNames().asList() == ['io.micronaut.visitors.TypeUseRuntimeAnn']
//    }
//
//    void "test recursive generic type parameter 2"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//final class Test<T extends Test> { // Missing argument
//}
//
//''')
//        expect:
//            def typeArguments = ce.getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.Test"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic type parameter 3"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//final class Test<T extends Test<T>> {
//}
//
//''')
//        expect:
//            def typeArguments = ce.getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.Test"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic type parameter 4"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//final class Test<T extends Test<?>> {
//}
//
//''')
//        expect:
//            def typeArguments = ce.getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.Test"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//import org.hibernate.SessionFactory;
//import org.hibernate.engine.spi.SessionFactoryDelegatingImpl;
//
//class MyFactory {
//
//    SessionFactory sessionFactory() {
//        return new SessionFactoryDelegatingImpl(null);
//    }
//}
//
//''')
//        expect:
//            def sessionFactoryMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("sessionFactory")).get()
//            def withOptionsMethod = sessionFactoryMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("withOptions")).get()
//            def typeArguments = withOptionsMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "org.hibernate.SessionBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return 2"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//class MyFactory {
//
//    MyBean myBean() {
//        return new MyBean();
//    }
//}
//
//interface MyBuilder<T extends MyBuilder> {
//    T build();
//}
//
//class MyBean {
//
//   MyBuilder<test.MyBuilder> myBuilder() {
//       return null;
//   }
//
//}
//
//''')
//        expect:
//            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
//            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
//            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.MyBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "test.MyBuilder"
//            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
//            def nextNextTypeArgument = nextNextTypeArguments.get("T")
//            nextNextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return 3"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//class MyFactory {
//
//    MyBean myBean() {
//        return new MyBean();
//    }
//}
//
//interface MyBuilder<T extends MyBuilder> {
//    T build();
//}
//
//class MyBean {
//
//   MyBuilder myBuilder() {
//       return null;
//   }
//
//}
//
//''')
//        expect:
//            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
//            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
//            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.MyBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return 4"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//class MyFactory {
//
//    MyBean myBean() {
//        return new MyBean();
//    }
//}
//
//interface MyBuilder<T extends MyBuilder> {
//    T build();
//}
//
//class MyBean {
//
//   MyBuilder<?> myBuilder() {
//       return null;
//   }
//
//}
//
//''')
//        expect:
//            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
//            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
//            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.MyBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "test.MyBuilder"
//            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
//            def nextNextTypeArgument = nextNextTypeArguments.get("T")
//            nextNextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return 5"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//class MyFactory {
//
//    MyBean myBean() {
//        return new MyBean();
//    }
//}
//
//interface MyBuilder<T extends MyBuilder> {
//    T build();
//}
//
//class MyBean {
//
//   MyBuilder<? extends MyBuilder> myBuilder() {
//       return null;
//   }
//
//}
//
//''')
//        expect:
//            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
//            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
//            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.MyBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "test.MyBuilder"
//            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
//            def nextNextTypeArgument = nextNextTypeArguments.get("T")
//            nextNextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return 6"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//class MyFactory {
//
//    MyBean myBean() {
//        return new MyBean();
//    }
//}
//
//interface MyBuilder<T extends MyBuilder> {
//    T build();
//}
//
//class MyBean<T extends MyBuilder> {
//
//   MyBuilder<T> myBuilder() {
//       return null;
//   }
//
//}
//
//''')
//        expect:
//            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
//            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
//            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.MyBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "test.MyBuilder"
//            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
//            def nextNextTypeArgument = nextNextTypeArguments.get("T")
//            nextNextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test recursive generic method return 7"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//class MyFactory {
//
//    MyBean myBean() {
//        return new MyBean();
//    }
//}
//
//interface MyBuilder<T extends MyBuilder> {
//    T build();
//}
//
//class MyBean<T extends MyBuilder> {
//
//   MyBuilder<? extends T> myBuilder() {
//       return null;
//   }
//
//}
//
//''')
//        expect:
//            def myBeanMethod = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("myBean")).get()
//            def myBuilderMethod = myBeanMethod.getReturnType().getEnclosedElement(ElementQuery.ALL_METHODS.named("myBuilder")).get()
//            def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
//            typeArguments.size() == 1
//            def typeArgument = typeArguments.get("T")
//            typeArgument.name == "test.MyBuilder"
//            def nextTypeArguments = typeArgument.getTypeArguments()
//            def nextTypeArgument = nextTypeArguments.get("T")
//            nextTypeArgument.name == "test.MyBuilder"
//            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
//            def nextNextTypeArgument = nextNextTypeArguments.get("T")
//            nextNextTypeArgument.name == "java.lang.Object"
//    }
//
//    void "test how the annotations from the type are propagated"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//import io.micronaut.inject.annotation.*;
//import io.micronaut.context.annotation.*;
//import java.util.List;
//import io.micronaut.visitors.Book;
//
//@jakarta.inject.Singleton
//class MyBean {
//
//    @Executable
//    public void saveAll(List<Book> books) {
//    }
//
//    @Executable
//    public <T extends Book> void saveAll2(List<? extends T> book) {
//    }
//
//    @Executable
//    public <T extends Book> void saveAll3(List<T> book) {
//    }
//
//    @Executable
//    public void save2(Book book) {
//    }
//
//    @Executable
//    public <T extends Book> void save3(T book) {
//    }
//
//    @Executable
//    public Book get() {
//        return null;
//    }
//}
//
//''')
//        when:
//            def saveAll = ce.findMethod("saveAll").get()
//            def listTypeArgument = saveAll.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            listTypeArgument.hasAnnotation(MyEntity.class)
//            listTypeArgument.hasAnnotation(Introspected.class)
//
//        when:
//            def saveAll2 = ce.findMethod("saveAll2").get()
//            def listTypeArgument2 = saveAll2.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            listTypeArgument2.hasAnnotation(MyEntity.class)
//            listTypeArgument2.hasAnnotation(Introspected.class)
//
//        when:
//            def saveAll3 = ce.findMethod("saveAll3").get()
//            def listTypeArgument3 = saveAll3.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            listTypeArgument3.hasAnnotation(MyEntity.class)
//            listTypeArgument3.hasAnnotation(Introspected.class)
//
//        when:
//            def save2 = ce.findMethod("save2").get()
//            def parameter2 = save2.getParameters()[0].getType()
//        then:
//            parameter2.hasAnnotation(MyEntity.class)
//            parameter2.hasAnnotation(Introspected.class)
//
//        when:
//            def save3 = ce.findMethod("save3").get()
//            def parameter3 = save3.getParameters()[0].getType()
//        then:
//            parameter3.hasAnnotation(MyEntity.class)
//            parameter3.hasAnnotation(Introspected.class)
//
//        when:
//            def get = ce.findMethod("get").get()
//            def returnType = get.getReturnType()
//        then:
//            returnType.hasAnnotation(MyEntity.class)
//            returnType.hasAnnotation(Introspected.class)
//    }
//
//    void "test how the type annotations from the type are propagated"() {
//        given:
//            ClassElement ce = buildClassElement('''\
//package test;
//
//import io.micronaut.inject.annotation.*;
//import io.micronaut.context.annotation.*;
//import java.util.List;
//import io.micronaut.visitors.Book;
//import io.micronaut.visitors.TypeUseRuntimeAnn;
//
//@jakarta.inject.Singleton
//class MyBean {
//
//    @Executable
//    public void saveAll(List<@TypeUseRuntimeAnn Book> books) {
//    }
//
//    @Executable
//    public <@TypeUseRuntimeAnn T extends Book> void saveAll2(List<? extends T> book) {
//    }
//
//    @Executable
//    public <@TypeUseRuntimeAnn T extends Book> void saveAll3(List<T> book) {
//    }
//
//    @Executable
//    public <T extends Book> void saveAll4(List<@TypeUseRuntimeAnn ? extends T> book) {
//    }
//
//    @Executable
//    public <T extends Book> void saveAll5(List<? extends @TypeUseRuntimeAnn T> book) {
//    }
//
//    @Executable
//    public void save2(@TypeUseRuntimeAnn Book book) {
//    }
//
//    @Executable
//    public <@TypeUseRuntimeAnn T extends Book> void save3(T book) {
//    }
//
//    @Executable
//    public <T extends @TypeUseRuntimeAnn Book> void save4(T book) {
//    }
//
//    @Executable
//    public <T extends Book> void save5(@TypeUseRuntimeAnn T book) {
//    }
//
//    @TypeUseRuntimeAnn
//    @Executable
//    public Book get() {
//        return null;
//    }
//}
//
//''')
//        when:
//            def saveAll = ce.findMethod("saveAll").get()
//            def listTypeArgument = saveAll.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument)
//
//        when:
//            def saveAll2 = ce.findMethod("saveAll2").get()
//            def listTypeArgument2 = saveAll2.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument2)
//
//        when:
//            def saveAll3 = ce.findMethod("saveAll3").get()
//            def listTypeArgument3 = saveAll3.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument3)
//
//        when:
//            def saveAll4 = ce.findMethod("saveAll4").get()
//            def listTypeArgument4 = saveAll4.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument4)
//
//        when:
//            def saveAll5 = ce.findMethod("saveAll5").get()
//            def listTypeArgument5 = saveAll5.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            validateBookArgument(listTypeArgument5)
//
//        when:
//            def save2 = ce.findMethod("save2").get()
//            def parameter2 = save2.getParameters()[0].getType()
//        then:
//            validateBookArgument(parameter2)
//
//        when:
//            def save3 = ce.findMethod("save3").get()
//            def parameter3 = save3.getParameters()[0].getType()
//        then:
//            validateBookArgument(parameter3)
//
//        when:
//            def save4 = ce.findMethod("save4").get()
//            def parameter4 = save4.getParameters()[0].getType()
//        then:
//            validateBookArgument(parameter4)
//
//        when:
//            def save5 = ce.findMethod("save5").get()
//            def parameter5 = save5.getParameters()[0].getType()
//        then:
//            validateBookArgument(parameter5)
//
//        when:
//            def get = ce.findMethod("get").get()
//            def returnType = get.getReturnType()
//        then:
//            validateBookArgument(returnType)
//    }
//
//    void validateBookArgument(ClassElement classElement) {
//        // The class element should have all the annotations present
//        assert classElement.hasAnnotation(TypeUseRuntimeAnn.class)
//        assert classElement.hasAnnotation(MyEntity.class)
//        assert classElement.hasAnnotation(Introspected.class)
//
//        def typeAnnotationMetadata = classElement.getTypeAnnotationMetadata()
//        // The type annotations should have only type annotations
//        assert typeAnnotationMetadata.hasAnnotation(TypeUseRuntimeAnn.class)
//        assert !typeAnnotationMetadata.hasAnnotation(MyEntity.class)
//        assert !typeAnnotationMetadata.hasAnnotation(Introspected.class)
//
//        // Get the actual type -> the type shouldn't have any type annotations
//        def type = classElement.getType()
//        assert !type.hasAnnotation(TypeUseRuntimeAnn.class)
//        assert type.hasAnnotation(MyEntity.class)
//        assert type.hasAnnotation(Introspected.class)
//        assert type.getTypeAnnotationMetadata().isEmpty()
//    }
//
//    void "test generics model"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test {
//    List<List<List<String>>> method1() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method1 = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method1")).get()
//            def genericType = method1.getGenericReturnType()
//            def genericTypeLevel1 = genericType.getTypeArguments()["E"]
//            !genericTypeLevel1.isGenericPlaceholder()
//            !genericTypeLevel1.isWildcard()
//            def genericTypeLevel2 = genericTypeLevel1.getTypeArguments()["E"]
//            !genericTypeLevel2.isGenericPlaceholder()
//            !genericTypeLevel2.isWildcard()
//            def genericTypeLevel3 = genericTypeLevel2.getTypeArguments()["E"]
//            !genericTypeLevel3.isGenericPlaceholder()
//            !genericTypeLevel3.isWildcard()
//
//            def type = method1.getReturnType()
//            def typeLevel1 = type.getTypeArguments()["E"]
//            !typeLevel1.isGenericPlaceholder()
//            !typeLevel1.isWildcard()
//            def typeLevel2 = typeLevel1.getTypeArguments()["E"]
//            !typeLevel2.isGenericPlaceholder()
//            !typeLevel2.isWildcard()
//            def typeLevel3 = typeLevel2.getTypeArguments()["E"]
//            !typeLevel3.isGenericPlaceholder()
//            !typeLevel3.isWildcard()
//    }
//
//    void "test generics model for wildcard"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test<T> {
//
//    List<?> method() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
//            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
//            !genericTypeArgument.isGenericPlaceholder()
//            !genericTypeArgument.isRawType()
//            genericTypeArgument.isWildcard()
//
//            def typeArgument = method.getReturnType().getTypeArguments()["E"]
//            !typeArgument.isGenericPlaceholder()
//            !typeArgument.isRawType()
//            typeArgument.isWildcard()
//    }
//
//    void "test generics model for placeholder"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test<T> {
//
//    List<T> method() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
//            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
//            genericTypeArgument.isGenericPlaceholder()
//            !genericTypeArgument.isRawType()
//            !genericTypeArgument.isWildcard()
//
//            def typeArgument = method.getReturnType().getTypeArguments()["E"]
//            typeArgument.isGenericPlaceholder()
//            !typeArgument.isRawType()
//            !typeArgument.isWildcard()
//    }
//
//    void "test generics model for class placeholder wildcard"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test<T> {
//
//    List<? extends T> method() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
//            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
//            !genericTypeArgument.isGenericPlaceholder()
//            !genericTypeArgument.isRawType()
//            genericTypeArgument.isWildcard()
//
//            def genericWildcard = genericTypeArgument as WildcardElement
//            !genericWildcard.lowerBounds
//            genericWildcard.upperBounds.size() == 1
//            def genericUpperBound = genericWildcard.upperBounds[0]
//            genericUpperBound.name == "java.lang.Object"
//            genericUpperBound.isGenericPlaceholder()
//            !genericUpperBound.isWildcard()
//            !genericUpperBound.isRawType()
//            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
//            genericPlaceholderUpperBound.variableName == "T"
//            genericPlaceholderUpperBound.declaringElement.get() == ce
//
//            def typeArgument = method.getReturnType().getTypeArguments()["E"]
//            !typeArgument.isGenericPlaceholder()
//            !typeArgument.isRawType()
//            typeArgument.isWildcard()
//
//            def wildcard = genericTypeArgument as WildcardElement
//            !wildcard.lowerBounds
//            wildcard.upperBounds.size() == 1
//            def upperBound = wildcard.upperBounds[0]
//            upperBound.name == "java.lang.Object"
//            upperBound.isGenericPlaceholder()
//            !upperBound.isWildcard()
//            !upperBound.isRawType()
//            def placeholderUpperBound = upperBound as GenericPlaceholderElement
//            placeholderUpperBound.variableName == "T"
//            placeholderUpperBound.declaringElement.get() == ce
//    }
//
//    void "test generics model for method placeholder wildcard"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test {
//
//    <T> List<? extends T> method() {
//        return null;
//    }
//}
//''')
//        expect:
//            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
//            method.getDeclaredTypeVariables().size() == 1
//            method.getDeclaredTypeVariables()[0].declaringElement.get() == method
//            method.getDeclaredTypeVariables()[0].variableName == "T"
//            method.getDeclaredTypeArguments().size() == 1
//            def placeholder = method.getDeclaredTypeArguments()["T"] as GenericPlaceholderElement
//            placeholder.declaringElement.get() == method
//            placeholder.variableName == "T"
//            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
//            !genericTypeArgument.isGenericPlaceholder()
//            !genericTypeArgument.isRawType()
//            genericTypeArgument.isWildcard()
//
//            def genericWildcard = genericTypeArgument as WildcardElement
//            !genericWildcard.lowerBounds
//            genericWildcard.upperBounds.size() == 1
//            def genericUpperBound = genericWildcard.upperBounds[0]
//            genericUpperBound.name == "java.lang.Object"
//            genericUpperBound.isGenericPlaceholder()
//            !genericUpperBound.isWildcard()
//            !genericUpperBound.isRawType()
//            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
//            genericPlaceholderUpperBound.variableName == "T"
//            genericPlaceholderUpperBound.declaringElement.get() == method
//
//            def typeArgument = method.getReturnType().getTypeArguments()["E"]
//            !typeArgument.isGenericPlaceholder()
//            !typeArgument.isRawType()
//            typeArgument.isWildcard()
//
//            def wildcard = genericTypeArgument as WildcardElement
//            !wildcard.lowerBounds
//            wildcard.upperBounds.size() == 1
//            def upperBound = wildcard.upperBounds[0]
//            upperBound.name == "java.lang.Object"
//            upperBound.isGenericPlaceholder()
//            !upperBound.isWildcard()
//            !upperBound.isRawType()
//            def placeholderUpperBound = upperBound as GenericPlaceholderElement
//            placeholderUpperBound.variableName == "T"
//            placeholderUpperBound.declaringElement.get() == method
//    }
//
//    void "test generics model for constructor placeholder wildcard"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test {
//
//    <T> Test(List<? extends T> list) {
//    }
//}
//''')
//        expect:
//            def method = ce.getPrimaryConstructor().get()
//            method.getDeclaredTypeVariables().size() == 1
//            method.getDeclaredTypeVariables()[0].declaringElement.get() == method
//            method.getDeclaredTypeVariables()[0].variableName == "T"
//            method.getDeclaredTypeArguments().size() == 1
//            def placeholder = method.getDeclaredTypeArguments()["T"] as GenericPlaceholderElement
//            placeholder.declaringElement.get() == method
//            placeholder.variableName == "T"
//            def genericTypeArgument = method.getParameters()[0].getGenericType().getTypeArguments()["E"]
//            !genericTypeArgument.isGenericPlaceholder()
//            !genericTypeArgument.isRawType()
//            genericTypeArgument.isWildcard()
//
//            def genericWildcard = genericTypeArgument as WildcardElement
//            !genericWildcard.lowerBounds
//            genericWildcard.upperBounds.size() == 1
//            def genericUpperBound = genericWildcard.upperBounds[0]
//            genericUpperBound.name == "java.lang.Object"
//            genericUpperBound.isGenericPlaceholder()
//            !genericUpperBound.isWildcard()
//            !genericUpperBound.isRawType()
//            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
//            genericPlaceholderUpperBound.variableName == "T"
//            genericPlaceholderUpperBound.declaringElement.get() == method
//
//            def typeArgument = method.getParameters()[0].getType().getTypeArguments()["E"]
//            !typeArgument.isGenericPlaceholder()
//            !typeArgument.isRawType()
//            typeArgument.isWildcard()
//
//            def wildcard = genericTypeArgument as WildcardElement
//            !wildcard.lowerBounds
//            wildcard.upperBounds.size() == 1
//            def upperBound = wildcard.upperBounds[0]
//            upperBound.name == "java.lang.Object"
//            upperBound.isGenericPlaceholder()
//            !upperBound.isWildcard()
//            !upperBound.isRawType()
//            def placeholderUpperBound = upperBound as GenericPlaceholderElement
//            placeholderUpperBound.variableName == "T"
//            placeholderUpperBound.declaringElement.get() == method
//    }
//
//    void "test generics equality"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//class Test<T extends Number> {
//
//    Number number;
//
//    <N extends T> Test(List<? extends N> list) {
//    }
//
//    <N extends T> List<? extends N> method1() {
//        return null;
//    }
//
//    List<? extends T> method2() {
//        return null;
//    }
//
//    T method3() {
//        return null;
//    }
//
//    List<List<? extends T>> method4() {
//        return null;
//    }
//
//    List<List<T>> method5() {
//        return null;
//    }
//
//    Test<T> method6() {
//        return null;
//    }
//
//    Test<?> method7() {
//        return null;
//    }
//
//    Test method8() {
//        return null;
//    }
//
//    <N extends T> Test<? extends N> method9() {
//        return null;
//    }
//
//    <N extends T> Test<? super N> method10() {
//        return null;
//    }
//}
//''')
//        expect:
//            def numberType = ce.getFields()[0].getType()
//            def constructor = ce.getPrimaryConstructor().get()
//            constructor.getParameters()[0].getGenericType().getTypeArguments(List).get("E") == numberType
//            constructor.getParameters()[0].getType().getTypeArguments(List).get("E") == numberType
//
//            ce.findMethod("method1").get().getGenericReturnType().getTypeArguments(List).get("E") == numberType
//            ce.findMethod("method1").get().getReturnType().getTypeArguments(List).get("E") == numberType
//
//            ce.findMethod("method2").get().getGenericReturnType().getTypeArguments(List).get("E") == numberType
//            ce.findMethod("method2").get().getReturnType().getTypeArguments(List).get("E") == numberType
//
//            ce.findMethod("method3").get().getGenericReturnType() == numberType
//            ce.findMethod("method3").get().getReturnType() == numberType
//
//            ce.findMethod("method4").get().getGenericReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
//            ce.findMethod("method4").get().getReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
//
//            ce.findMethod("method5").get().getGenericReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
//            ce.findMethod("method5").get().getReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
//
//            ce.findMethod("method6").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
//            ce.findMethod("method6").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
//
//            ce.findMethod("method7").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
//            ce.findMethod("method7").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
//
//            ce.findMethod("method8").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
//            ce.findMethod("method8").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
//
//            ce.findMethod("method9").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
//            ce.findMethod("method9").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
//
//            ce.findMethod("method10").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
//            ce.findMethod("method10").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
//    }
//
//    void "test inherit parameter annotation"() {
//        ClassElement ce = buildClassElement('''
//package test;
//import java.util.List;
//
//interface MyApi {
//
//    String get(@io.micronaut.visitors.MyParameter("X-username") String username);
//}
//
//class UserController implements MyApi {
//
//    @Override
//    public String get(String username) {
//        return null;
//    }
//
//}
//
//''')
//        expect:
//            ce.findMethod("get").get().getParameters()[0].hasAnnotation(MyParameter)
//    }

    void "test interface placeholder"() {
        ClassElement ce = buildClassElement('test.MyRepo', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

class MyRepo : Repo<MyBean, Long>
interface Repo<E, ID> : GenericRepository<E, ID>
interface GenericRepository<E, ID>
@Prototype
class MyBean {
    var name: String? = null
}

''')

        when:
            def repo = ce.getTypeArguments("test.Repo")
        then:
            repo.get("E").simpleName == "MyBean"
            repo.get("E").getSyntheticBeanProperties().size() == 1
            repo.get("E").getMethods().size() == 0
            repo.get("E").getFields().size() == 1
            repo.get("E").getFields().get(0).name == "name"
        when:
            def genRepo = ce.getTypeArguments("test.GenericRepository")
        then:
            genRepo.get("E").simpleName == "MyBean"
            genRepo.get("E").getSyntheticBeanProperties().size() == 1
            genRepo.get("E").getMethods().size() == 0
            genRepo.get("E").getFields().get(0).name == "name"
    }


    private void assertListGenericArgument(ClassElement type, Closure cl) {
        def arg1 = type.getAllTypeArguments().get(List.class.name).get("E")
        def arg2 = type.getAllTypeArguments().get(Collection.class.name).get("E")
        def arg3 = type.getAllTypeArguments().get(Iterable.class.name).get("T")
        cl.call(arg1)
        cl.call(arg2)
        cl.call(arg3)
    }

    private void assertMethodsByName(List<MethodElement> allMethods, String name, List<String> expectedDeclaringTypeSimpleNames) {
        Collection<MethodElement> methods = collectElements(allMethods, name)
        assert expectedDeclaringTypeSimpleNames.size() == methods.size()
        for (String expectedDeclaringTypeSimpleName : expectedDeclaringTypeSimpleNames) {
            assert oneElementPresentWithDeclaringType(methods, expectedDeclaringTypeSimpleName)
        }
    }

    private void assertFieldsByName(List<FieldElement> allFields, String name, List<String> expectedDeclaringTypeSimpleNames) {
        Collection<FieldElement> fields = collectElements(allFields, name)
        assert expectedDeclaringTypeSimpleNames.size() == fields.size()
        for (String expectedDeclaringTypeSimpleName : expectedDeclaringTypeSimpleNames) {
            assert oneElementPresentWithDeclaringType(fields, expectedDeclaringTypeSimpleName)
        }
    }

    private boolean oneElementPresentWithDeclaringType(Collection<MemberElement> elements, String declaringTypeSimpleName) {
        elements.stream()
                .filter { it -> it.getDeclaringType().getSimpleName() == declaringTypeSimpleName }
                .count() == 1
    }

    static <T extends Element> Collection<T> collectElements(List<T> allElements, String name) {
        return allElements.findAll { it.name == name }
    }
}
