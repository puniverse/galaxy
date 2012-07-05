.. _man-config-spring:

###############
A Spring Primer
###############

The `Spring Framework`_ lets applications configure and wire their components in an XML file (it does many other things as well, but Galaxy only uses Spring for configuration).
Using Spring or configuration allows very fine-tined control over many aspects of the application, but it can be quite verbose.
This section is a short tutorial explaining how to configure and wire components using Spring XML. 
For a more detailed reference of Spring configuration please see `Spring's IoC Container Documentation`_.

.. _`Spring Framework`: http://www.springsource.org/spring-framework
.. _`Spring's IoC Container Documentation`: http://static.springsource.org/spring/docs/3.1.x/spring-framework-reference/html/beans.html

.. _man-config-spring-xml:

Spring XML
==========

A Spring configuration file is an XML file containing bean definitions (beans are explained in the next section).
Here's the structure of the configuration file, as should be used for Galaxy configuration. It contains some required Spring schemas
as well as a few optional ones that provide some convenient features:
	
.. code-block:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <beans xmlns="http://www.springframework.org/schema/beans"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xmlns:context="http://www.springframework.org/schema/context"
           xmlns:util="http://www.springframework.org/schema/util"

           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
              http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-3.0.xsd
              http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util-3.0.xsd"
           default-lazy-init="false"
           default-autowire="constructor">

        <!-- bean definitions ... -->
    </beans>

.. _man-config-spring-beans:

Instantiating beans
===================

Spring **beans** are application components declared, configured in wired in the Spring XML configuration file as ``bean`` XML elements. 
Each bean corresponds to an instance of a Java object. A bean has a unique ID, which names the component, and a means to instantiate it.
Usually, a bean is instantiated by constructing a simple object of a given class, in which case the bean definition would look like this.

.. code-block:: xml

    <bean id="myBean" class="com.mycompany.myClass"/>

This would instantiate an instance (called ``myBean``) of the class ``com.mycompany.myClass``.

Sometimes, beans are constructed not using a simple constructor but by calling a factory static method. In that case, the bean definition
would look like this:

.. code-block:: xml

    <bean id="myBean" class="com.mycompany.myFactoryClass" factory-method="createInstance"/>

This would instantiate a bean (of some unspecified class) by calling the ``createInstance`` static method of the ``com.mycompany.myFactoryClass`` class.

.. _man-config-spring-config:

Configuring beans
=================

Usually, a bean definition includes some configuration of the bean instance. 
There are two kinds of configuration hooks: **constructor-args** and **properties**. 
Each bean definition can include constructor-args as well as properties.

constructor-args
----------------

**constructor-args** are passed as either constructor arguments to the class constructor or as arguments to the static factory method, 
according to the instantiation mechanism used. They are usually required for a successful bean instantiation.

constructor-args can be passed by name, by index, or by type. Here's how we use them to instantiate a bean, using names:

.. code-block:: xml

    <bean id="myBean" class="com.mycompany.myClass">
        <constructor-arg name="myName" value="foo" />
        <constructor-arg name="size" value="5" />        
    </bean>

Here we've passed the value ``"foo"`` to the ``String`` parameter ``myName`` of the constructor, and the value ``5`` to the ``size`` parameter
(in the next section we'll see how Spring knows which value type to use).

Unfortunately, specifying constructor-args by name does not always work - it requires either relevant debug information in the class file or the
use of a specific annotation by the class's author. (Note: all Galaxy components described in this documentation, i.e. those that belong to a
``co.paralleluniverse.*`` packages, are annotated and allow specifying constructor-args by name). If named constructor-args don't work, we can
specify them using indices like so:

.. code-block:: xml

    <bean id="myBean" class="com.mycompany.myClass">
        <constructor-arg index="0" value="foo" />
        <constructor-arg index="1" value="5" />        
    </bean>

This also passes the String value ``"foo"`` as the first argument of the constructor (which is supposedly ``myName``), and the
value ``5`` as the second argument.

If each parameter is of a different type, we can also pass constructor-args without specifying either name or index, like so:

.. code-block:: xml

    <bean id="myBean" class="com.mycompany.myClass">
        <constructor-arg value="foo" />
        <constructor-arg value="5" />        
    </bean>

This will achieve the same effect, but only if Spring can figure out unambiguously the arguments' types and which parameter they correspond to;
if not, Spring will issue an error.

properties
----------

**properties** are configurations that are usually optional, and if not included in the bean definition, the bean will use some default values.
Internally, properties are set by calling Java Bean setter methods, and can therefore always be specified by name, like so:

.. code-block:: xml

    <bean id="otherBean" class="com.mycompany.otherClass">
        <property name="foo" value="bar" />
        <property name="colorName" value="blue" />
        <property name="width" value="10" />
        <property name="height" value="100" />
    </bean>

Of course, some beans combine both constructor-args and properties.

.. _man-config-spring-p-c:

The p- and c- namespaces
========================

Spring provides two special XML namespaces called ``p`` and ``c`` that allow for a less verbose configuration in many cases.

Using the ``p`` and ``c`` namespaces, you can specify configuration values as XML attributes rather than as child elements.
So the following definition, using the ``p`` and ``c`` namespaces:

.. code-block:: xml

    <bean id="myBean" class="a.b.Foo" c:text="Hello World!" p:height="300" p:color="blue" />

is identical to:

.. code-block:: xml

    <bean id="myBean" class="a.b.Foo">
        <constructor-arg name="text" value="Hello World!" />
        <property name="height" value="300" />
        <property name="color" value="blue" />
    </bean>

If constructor-arg names do not work with a given class, you can use indices with the ``c`` namespace, with

.. code-block:: xml

    <bean id="myBean" class="a.b.Bar" c:_0="100" c:_1="200" />

being the same as

.. code-block:: xml

    <bean id="myBean" class="a.b.Bar">
        <constructor-arg index="0" value="100" />
        <constructor-arg index="0" value="200" />
    </bean>


.. _man-config-spring-values:

Values
======

This section explains how Spring interprets property (or constructor-arg) values.

.. _man-config-spring-values-primitives:

Primitives, strings and enums
-----------------------------

Spring uses Java reflection to figure out the type of the property (or constructor-arg), and it attempts to convert the string
value in the XML document into the appropriate type. So ``value="5"`` would become the integer ``5`` if the property's type is
``int``,  ``5.0`` if it's ``double``, or the string ``"5"`` if the property is of type ``String``; 
The values ``true`` and ``false`` are thus converted to ``boolean`` if the property is of the ``boolean`` type,
and strings are passed verbatim if the property is of type ``String``.

If a property's type is a Java enum, then its value is converted to the appropriate enum value according to its name,
so ``value="NFC"`` would become ``java.text.Normalizer.Form.NFC`` if the property's type is ``java.text.Normalizer.Form``.

.. _man-config-spring-values-null:

Null values
-----------

If a property (or a constructor-arg), accept a Java reference, then the ``null`` value can be passed like this:

.. code-block:: xml

    <property name="foo"><null/></property>

.. _man-config-spring-values-ref:

Referencing other beans
-----------------------

When a property (or constructor-arg) take an object, we can pass it one of the beans we've defined.
So, if we have this bean:

.. code-block:: xml

    <bean id="fooBean" class="a.b.Foo" />

and the ``Bar`` class has a property (or constructor-arg) taking a ``Foo``, we can pass it ``fooBean`` like so:


.. code-block:: xml

    <bean id="myBar" class="a.b.Bar" />
        <property name="foo" ref="fooBean" />
    </bean>

Notice the use of the ``ref`` attribute rather than ``value``.

The same could be done with the c/p- namespaces: ``p:foo-ref="myFoo"``, ``c:foo-ref="myFoo"`` or ``c:_0-ref="myFoo"``.

.. _man-config-spring-values-inner:

Compound values (inner beans)
-----------------------------

When a property (or a constructor-arg) take an object, we could use a ``ref``, but if the object is to be used only for
the purpose of setting this particular property, rather than define a new bean and reference it, it is less verbose,
less cluttered, and generally preferable to use an "inner bean". 

Inner beans are bean definitions local to a single property (or constructor arg), that don't have an ``id``, and therefore
cannot be referenced anywhere else.

Here's an example from a Galaxy configuration file of setting a property that takes a value of type ``java.net.InetSocketAddress``:

.. code-block:: xml

    <property name="multicastGroup">
        <bean class="java.net.InetSocketAddress">
            <constructor-arg index="0" value="225.0.0.1"/>
            <constructor-arg index="1" value="7050"/>
        </bean>
    </property>

Or, more succinctly:

.. code-block:: xml

    <property name="multicastGroup">
        <bean class="java.net.InetSocketAddress" c:_0="225.0.0.1" c:_1="7050" />
    </property>
