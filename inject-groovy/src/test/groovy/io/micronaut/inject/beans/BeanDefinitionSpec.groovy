package io.micronaut.inject.beans

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition

class BeanDefinitionSpec extends AbstractBeanDefinitionSpec {

    void "test limit the exposed bean types"() {
        given:
        def definition = buildBeanDefinition('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = Runnable)
class Test implements Runnable {
    void run() {}
}

''')
        expect:
        definition.exposedTypes == [Runnable] as Set
    }

    void "test limit the exposed bean types - reference"() {
        given:
        def reference = buildBeanDefinitionReference('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = Runnable)
class Test implements Runnable {
    void run() {}
}

''')
        expect:
        reference.exposedTypes == [Runnable] as Set
    }

    void "test exposed types on factory with AOP"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes

import io.micronaut.aop.Logged
import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean(typed = X)
    @Logged
    Y method() {
        new Y()
    }
}

interface X {

}
class Y implements X {

}

''')

        then:
        noExceptionThrown()
    }

    void "test fail compilation on invalid exposed bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = Runnable)
class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [java.lang.Runnable] that is not implemented by the bean type")
    }

    void "test fail compilation on exposed subclass of bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = X)
class Test {

}

class X extends Test {}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.X] that is not implemented by the bean type")
    }

    void "test fail compilation on exposed subclass of bean type with factory"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean(typed = [X, Y])
    X method() {
        new Y()
    }
}

interface X {}
class Y implements X {}

''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Y] that is not implemented by the bean type")
    }

    void "test exposed bean types with factory invalid type"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean(typed = Z)
    X method() {
        new Y()
    }
}

interface Z { }
interface X { }
class Y implements X { }
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Z] that is not implemented by the bean type")
    }

    void 'test order annotation'() {
        given:
        def definition = buildBeanDefinition('test.TestOrder', '''
package test;

import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
@Singleton
@Order(value = 10)
class TestOrder {

}
''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void 'test order annotation inner class bean'() {
        given:
        def definition = buildBeanDefinition('test.OuterBean$TestOrder', '''
package test;

import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

class OuterBean {

    static interface OrderedBean {
    }

    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(value = 10)
    static class TestOrder implements OrderedBean {

    }
}

''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void "test a bean definition in a package with uppercase letters"() {
        when:
        def definition = buildBeanDefinition('test.A', 'TestBean', '''
package test.A;

@jakarta.inject.Singleton
class TestBean {

}
''')
        then:
        noExceptionThrown()
        definition != null
    }

    void "test isTypeVariable"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
package test;
import javax.validation.constraints.*;
import java.util.List;

@jakarta.inject.Singleton
public class Test implements Serde<Object> {
}

interface Serde<T> extends Serializer<T>, Deserializer<T> {
}

interface Serializer<T> {
}

interface Deserializer<T> {
}


        ''')

        when: "Micronaut Serialization use-case"
            def serdeTypeParam = definition.getTypeArguments("test.Serde")[0]
            def serializerTypeParam = definition.getTypeArguments("test.Serializer")[0]
            def deserializerTypeParam = definition.getTypeArguments("test.Deserializer")[0]

        then: "The first is a placeholder"
            serdeTypeParam.isTypeVariable() //
            (serdeTypeParam instanceof GenericPlaceholder)
        and:
            serializerTypeParam.isTypeVariable()
            (serializerTypeParam instanceof GenericPlaceholder)
            deserializerTypeParam.isTypeVariable()
            (deserializerTypeParam instanceof GenericPlaceholder)
    }

    void "test isTypeVariable array"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
package test;
import javax.validation.constraints.*;
import java.util.List;

@jakarta.inject.Singleton
public class Test implements Serde<String[]> {
}

interface Serde<T> extends Serializer<T>, Deserializer<T> {
}

interface Serializer<T> {
}

interface Deserializer<T> {
}


        ''')

        when: "Micronaut Serialization use-case"
            def serdeTypeParam = definition.getTypeArguments("test.Serde")[0]
            def serializerTypeParam = definition.getTypeArguments("test.Serializer")[0]
            def deserializerTypeParam = definition.getTypeArguments("test.Deserializer")[0]
        // Arrays are not resolved as GroovyClassElements or placeholders
        then: "The first is a placeholder"
            serdeTypeParam.simpleName == "String[]"
            !serdeTypeParam.isTypeVariable()
            !(serdeTypeParam instanceof GenericPlaceholder)
        and: "threat resolved placeholder as not a type variable"
            serializerTypeParam.simpleName == "String[]"
            !serializerTypeParam.isTypeVariable()
            !(serializerTypeParam instanceof GenericPlaceholder)
            deserializerTypeParam.simpleName == "String[]"
            !deserializerTypeParam.isTypeVariable()
            !(deserializerTypeParam instanceof GenericPlaceholder)
    }

}
