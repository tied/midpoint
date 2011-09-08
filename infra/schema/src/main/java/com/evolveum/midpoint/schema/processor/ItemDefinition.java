/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */

package com.evolveum.midpoint.schema.processor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.evolveum.midpoint.schema.exception.SchemaException;

/**
 * Abstract item definition in the schema.
 * 
 * This is supposed to be a superclass for all item definitions. Items are things
 * that can appear in property containers, which generally means only a property
 * and property container itself. Therefore this is in fact superclass for those
 * two definitions.
 * 
 * The definitions represent data structures of the schema. Therefore instances
 * of Java objects from this class represent specific <em>definitions</em> from
 * the schema, not specific properties or objects. E.g the definitions does not
 * have any value.
 * 
 * To transform definition to a real property or object use the explicit
 * instantiate() methods provided in the definition classes. E.g. the
 * instantiate() method will create instance of Property using appropriate
 * PropertyDefinition.
 * 
 * The convenience methods in Schema are using this abstract class to find
 * appropriate definitions easily.
 * 
 * @author Radovan Semancik
 * 
 */
public abstract class ItemDefinition extends Definition implements Serializable {

	private static final long serialVersionUID = -2643332934312107274L;
	protected QName name;

	// TODO: annotations
	
	/**
	 * Default constructor.
	 * The constructors should be used only occasionally (if used at all).
	 * Use the factory methods in the ResourceObjectDefintion instead.
	 */
	ItemDefinition(){
		super();
	}

	/**
	 * The constructors should be used only occasionally (if used at all).
	 * Use the factory methods in the ResourceObjectDefintion instead.
	 * 
	 * @param name definition name (element Name)
	 * @param defaultName default element name
	 * @param typeName type name (XSD complex or simple type)
	 */
	ItemDefinition(QName name, QName defaultName, QName typeName) {
		super(defaultName,typeName);
		this.name = name;
	}
	
	/**
	 * Returns name of the defined entity.
	 * 
	 * The name is a name of the entity instance if it is fixed by the schema.
	 * E.g. it may be a name of the property in the container that cannot be
	 * changed.
	 * 
	 * The name corresponds to the XML element name in the XML representation of
	 * the schema. It does NOT correspond to a XSD type name.
	 * 
	 * Name is optional. If name is not set the null value is returned. If name is
	 * not set the type is "abstract", does not correspond to the element.
	 * 
	 * @return the name name of the entity or null.
	 */
	public QName getName() {
		return name;
	}

	/**
	 * Returns either name (if specified) or default name.
	 * 
	 * Convenience method.
	 * 
	 * @return name or default name
	 */
	public QName getNameOrDefaultName() {
		if (name != null) {
			return name;
		}
		return defaultName;
	}
	
	/**
	 * Create an item instance. Definition name or default name will
	 * used as an element name for the instance. The instance will otherwise be empty.
	 * @return created item instance
	 */
	abstract public Item instantiate();

	/**
	 * Create an item instance. Definition name will use provided name.
	 * for the instance. The instance will otherwise be empty.
	 * @return created item instance
	 */
	abstract public Item instantiate(QName name);
	
	/**
	 * Create at instance of the item initialized from the provided list of elements.
	 * The definition name (ore default name) will be used as the instance (element) name.
	 * 
	 * @param elements content of the item
	 * @return created item instance initialized with content
	 * @throws SchemaException error parsing the provided elements
	 */
	abstract public Item parseItem(List<Object> elements) throws SchemaException;
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + ":" + getName() + " ("+getTypeName()+")";
	}
	
}
