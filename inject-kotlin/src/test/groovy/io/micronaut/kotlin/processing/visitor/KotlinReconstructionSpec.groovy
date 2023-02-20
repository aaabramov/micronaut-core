package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import spock.lang.Unroll

class KotlinReconstructionSpec extends AbstractKotlinCompilerSpec {

    @Unroll("field type is #fieldType")
    def 'field type'() {
        given:
            def element = buildClassElement("example.Test", """
package example;

import java.util.*;

class Test<T> {
    lateinit var field : $fieldType
}

class Lst<in E> {
}
""")
            def field = element.getFields()[0]

        expect:
            reconstructTypeSignature(field.genericType) == fieldType

        where:
            fieldType << [
                    'String',
                    'List<String>',
                    'List<Any>',
                    'List<T>',
                    'List<Array<T>>',
                    'List<out CharSequence>',
                    'List<out Array<T>>',
                    'List<out Array<T>>',
                    'List<out Array<List<out Array<T>>>>',
                    'Lst<String>',
                    'Lst<in CharSequence>',
                    'Lst<Lst<in String>>'
            ]
    }

    @Unroll("type var is #decl")
    def 'type vars declared on method'() {
        given:
            def element = buildClassElement("example.Test", """
package example;

import java.util.*;

abstract class Test<A> {
    fun <$decl> method() {}
}
class Lst<in E> {
}
""")
            def method = element.<MethodElement> getEnclosedElement(ElementQuery.ALL_METHODS.named(s -> s == 'method')).get()

        expect:
            reconstructTypeSignature(method.declaredTypeVariables[0], true) == decl

        where:
            decl << [
//                    'T',
                    'T : CharSequence',
                    'T : A',
                    'T : List<*>',
                    'T : List<T>',
                    'T : List<out T>',
                    'T : List<out A>',
                    'T : List<Array<T>>',
                    'T : Lst<in T>',
                    'T : Lst<in A>',
                    'T : Lst<Array<T>>'
            ]
    }

    @Unroll("super type is #superType")
    def 'super type'() {
        given:
            def element = buildClassElement("example.Test", """
package example;

import java.util.*;

abstract class Test<T> : $superType() {
}
""")

        expect:
            reconstructTypeSignature(element.superType.get()) == superType

        where:
            superType << [
                    'AbstractList<String>',
                    'AbstractList<T>',
                    'AbstractList<Array<T>>',
                    'AbstractList<List<out CharSequence>>',
                    'AbstractList<List<out Array<T>>>',
                    'AbstractList<Array<List<out Array<T>>>>',
                    'AbstractList<List<*>>'
            ]
    }

    @Unroll("super interface is #superType")
    def 'super interface'() {
        given:
            def element = buildClassElement("example.Test", """
package example;

import java.util.*;

abstract class Test<T> : $superType {
}
""")

        expect:
            reconstructTypeSignature(element.interfaces[0]) == superType

        where:
            superType << [
                    'List<String>',
                    'List<T>',
                    'List<Array<T>>',
                    'List<List<out CharSequence>>',
                    'List<List<out Array<T>>>',
                    'List<Array<List<out Array<T>>>>',
                    'List<List<Any>>',
            ]
    }

    @Unroll("type var is #decl")
    def 'type vars declared on type'() {
        given:
            def element = buildClassElement("example.Test", """
package example;

import java.util.*;

abstract class Test<A, $decl> {
}

class Lst<in E> {
}
""")

        expect:
            reconstructTypeSignature(element.declaredGenericPlaceholders[1], true) == decl

        where:
            decl << [
//                  'T',
                    'T : A',
                    'T : List<*>',
                    'T : List<T>',
                    'T : List<out T>',
                    'T : List<out A>',
                    'T : List<Array<T>>',
                    'T : Lst<in A>',
                    'T : Lst<in T>',
                    'T : Lst<in Array<T>>'
            ]
    }

    @Unroll('declaration is #decl')
    def 'fold type variable to null'() {
        given:
            def classElement = buildClassElement("example.Test", """
package example;

import java.util.*;

class Test<T> {
    lateinit var field : $decl;
}

class Lst<in E> {
}
""")
            def fieldType = classElement.fields[0].type

        expect:
            reconstructTypeSignature(fieldType.foldBoundGenericTypes {
                if (it != null && it.isGenericPlaceholder() && ((GenericPlaceholderElement) it).variableName == 'T') {
                    return null
                } else {
                    return it
                }
            }) == expected

        where:
            decl             | expected
            'String'         | 'String'
            'List<T>'        | 'List'
            'Map<Object, T>' | 'Map'
            'List<out T>'    | 'List'
            'Lst<in T>'      | 'Lst'
    }

    @Unroll("field type is #fieldType")
    def 'bound field type'() {
        given:
            def element = buildClassElement("example.Wrapper", """
package example;

import java.util.*;

class Wrapper {
    var test: Test<String>? = null;
}
class Test<T> {
    var field: $fieldType? = null;
}
class Lst<in E> {
}
""")
            def field = element.getFields()[0].genericType.getFields()[0]

        expect:
            reconstructTypeSignature(field.genericType) == expectedType

        where:
            fieldType                             | expectedType
            'String'                              | 'String'
            'List<String>'                        | 'List<String>'
            'List<*>'                             | 'List<*>'
            'List<T>'                             | 'List<String>'
            'List<Array<T>>'                      | 'List<Array<String>>'
            'List<out CharSequence>'              | 'List<out CharSequence>'
            'Lst<in String>'                      | 'Lst<in String>'
            'List<out Array<T>>'                  | 'List<out Array<String>>'
            'List<out Array<List<out Array<T>>>>' | 'List<out Array<List<out Array<String>>>>'
            'List<out List<*>>'                   | 'List<out List<*>>'
    }


    @Unroll("field type is #fieldType")
    def 'bound field type to other variable'() {
        given:
            def element = buildClassElement("example.Wrapper", """
package example;

import java.util.*;

class Wrapper<U> {
    var test: Test<U>? = null;
}
class Test<T> {
    var field: $fieldType? = null;
}
class Lst<in E> {
}
""")
            def field = element.getFields()[0].genericType.getFields()[0]

        expect:
            reconstructTypeSignature(field.genericType) == expectedType

        where:
            fieldType                             | expectedType
            'String'                              | 'String'
            'List<String>'                        | 'List<String>'
            'List<*>'                             | 'List<*>'
            'List<T>'                             | 'List<U>'
            'List<Array<T>>'                      | 'List<Array<U>>'
            'List<out CharSequence>'              | 'List<out CharSequence>'
            'Lst<in String>'                      | 'Lst<in String>'
            'List<out Array<T>>'                  | 'List<out Array<U>>'
            'List<out Array<List<out Array<T>>>>' | 'List<out Array<List<out Array<U>>>>'
            'List<out List<*>>'                   | 'List<out List<*>>'
    }

    def 'unbound super type'() {
        given:
            def superElement = buildClassElement("example.Sub", """
package example;

import java.util.*;

class Sub<U> : Sup<$params>() {
}
open class Sup<$decl> {
}
class Lst<in E> {
}
""")
            def interfaceElement = buildClassElement("example.Sub", """
package example;

import java.util.*;

class Sub<U> : Sup<$params> {
}
interface Sup<$decl> {
}
class Lst<in E> {
}
""")

        expect:
            reconstructTypeSignature(superElement.getSuperType().get()) == expected
            reconstructTypeSignature(interfaceElement.getInterfaces()[0]) == expected

        where:
            decl | params        | expected
            'T'  | 'String'      | 'Sup<String>'
            'T'  | 'List<U>'     | 'Sup<List<U>>'
            'T'  | 'List<out U>' | 'Sup<List<out U>>'
            'T'  | 'Lst<in U>'   | 'Sup<Lst<in U>>'
    }

    def 'bound super type'() {
        given:
            def superElement = buildClassElementTransformed("example.Sub", """
package example;

import java.util.*;

class Sub<U> : Sup<$params>() {
}
open class Sup<$decl> {
}
class Lst<in E> {
}
""", { ce ->
                ce = ce.withTypeArguments([ClassElement.of(String)])
                ce.getSuperType().ifPresent {st -> st.getAllTypeArguments()}
                return ce
            })
            def interfaceElement = buildClassElementTransformed("example.Sub", """
package example;

import java.util.*;

class Sub<U> : Sup<$params> {
}
interface Sup<$decl> {
}
class Lst<in E> {
}
""", { ce ->
                ce = ce.withTypeArguments([ClassElement.of(String)])
                ce.getInterfaces().forEach {it -> it.getAllTypeArguments()}
                return ce
            })

        expect:
            reconstructTypeSignature(superElement.getSuperType().get()) == expected
            reconstructTypeSignature(interfaceElement.getInterfaces()[0]) == expected

        where:
            decl | params        | expected
            'T'  | 'String'      | 'Sup<String>'
            'T'  | 'List<U>'     | 'Sup<List<String>>'
            'T'  | 'List<out U>' | 'Sup<List<out String>>'
            'T'  | 'Lst<in U>'   | 'Sup<Lst<in String>>'
    }

    @Unroll('declaration is #decl')
    def 'fold type variable'() {
        given:
            def fieldType = buildClassElementTransformed("example.Test", """
package example;

import java.util.*;

class Test<T> {
    var field : $decl? = null;
}
class Lst<in E> {
}
""", {
                def fieldType = it.fields[0].type.foldBoundGenericTypes {
                    if (it.isGenericPlaceholder() && ((GenericPlaceholderElement) it).variableName == 'T') {
                        return ClassElement.of(String)
                    } else {
                        return it
                    }
                }
                fieldType.getAllTypeArguments()
                return fieldType
            })

        expect:
            reconstructTypeSignature(fieldType) == expected

        where:
            decl          | expected
            'String'      | 'String'
            'T'           | 'String'
            'List<T>'     | 'List<String>'
            'Map<Any, T>' | 'Map<Any, String>'
            'List<out T>' | 'List<out String>'
            'Lst<in T>'   | 'Lst<in String>'
    }
}
