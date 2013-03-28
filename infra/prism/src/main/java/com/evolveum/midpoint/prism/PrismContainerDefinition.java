/*
 * Copyright (c) 2012 Evolveum
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
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.prism;

import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.NameItemPathSegment;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugDumpable;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * Definition of a property container.
 * <p/>
 * Property container groups properties into logical blocks. The reason for
 * grouping may be as simple as better understandability of data structure. But
 * the group usually means different meaning, source or structure of the data.
 * For example, the property container is frequently used to hold properties
 * that are dynamic, not fixed by a static schema. Such grouping also naturally
 * translates to XML and helps to "quarantine" such properties to avoid Unique
 * Particle Attribute problems.
 * <p/>
 * Property Container contains a set of (potentially multi-valued) properties.
 * The order of properties is not significant, regardless of the fact that it
 * may be fixed in the XML representation. In the XML representation, each
 * element inside Property Container must be either Property or a Property
 * Container.
 * <p/>
 * This class represents schema definition for property container. See
 * {@link Definition} for more details.
 *
 * @author Radovan Semancik
 */
public class PrismContainerDefinition<V extends Containerable> extends ItemDefinition {

    private static final long serialVersionUID = -5068923696147960699L;
    
    protected ComplexTypeDefinition complexTypeDefinition;
    protected Class<V> compileTimeClass;
 
    /**
     * This means that the property container is not defined by fixed (compile-time) schema.
     * This in fact means that we need to use getAny in a JAXB types. It does not influence the
     * processing of DOM that much, as that does not really depend on compile-time/run-time distinction.
     */
    protected boolean isRuntimeSchema;

    /**
     * The constructors should be used only occasionally (if used at all).
     * Use the factory methods in the ResourceObjectDefintion instead.
     */
    public PrismContainerDefinition(QName name, ComplexTypeDefinition complexTypeDefinition, PrismContext prismContext) {
        this(name, complexTypeDefinition, prismContext, null);
    }

    public PrismContainerDefinition(QName name, ComplexTypeDefinition complexTypeDefinition, PrismContext prismContext, 
    		Class<V> compileTimeClass) {
        super(name, determineDefaultName(complexTypeDefinition), determineTypeName(complexTypeDefinition), prismContext);
        this.complexTypeDefinition = complexTypeDefinition;
        if (complexTypeDefinition == null) {
            isRuntimeSchema = true;
            super.setDynamic(true);
        } else {
            isRuntimeSchema = complexTypeDefinition.isXsdAnyMarker();
            super.setDynamic(isRuntimeSchema);
        }
		this.compileTimeClass = compileTimeClass;
    }

    private static QName determineTypeName(ComplexTypeDefinition complexTypeDefinition) {
        if (complexTypeDefinition == null) {
            // Property container without type: xsd:any
            // FIXME: this is kind of hack, but it works now
            return DOMUtil.XSD_ANY;
        }
        return complexTypeDefinition.getTypeName();
    }

    private static QName determineDefaultName(ComplexTypeDefinition complexTypeDefinition) {
        if (complexTypeDefinition == null) {
            return null;
        }
        return complexTypeDefinition.getDefaultName();
    }

    public Class<V> getCompileTimeClass() {
		if (compileTimeClass != null) {
			return compileTimeClass;
		}
		if (complexTypeDefinition == null) {
			return null;
		}
		return (Class<V>) complexTypeDefinition.getCompileTimeClass();	
	}

	public void setCompileTimeClass(Class<V> compileTimeClass) {
		this.compileTimeClass = compileTimeClass;
	}
    
    protected String getSchemaNamespace() {
        return getNameOrDefaultName().getNamespaceURI();
    }

    public ComplexTypeDefinition getComplexTypeDefinition() {
        return complexTypeDefinition;
    }

    public void setComplexTypeDefinition(ComplexTypeDefinition complexTypeDefinition) {
        this.complexTypeDefinition = complexTypeDefinition;
    }

    @Override
	void revive(PrismContext prismContext) {
		if (this.prismContext != null) {
			return;
		}
		this.prismContext = prismContext;
		if (complexTypeDefinition != null) {
			complexTypeDefinition.revive(prismContext);
		}
	}

	public <D extends ItemDefinition> D findItemDefinition(QName name, Class<D> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("type not specified while searching for " + name + " in " + this);
        }
        if (name == null) {
            throw new IllegalArgumentException("name not specified while searching in " + this);
        }

        for (ItemDefinition def : getDefinitions()) {
            if (isItemValid(def, name, clazz)) {
                return (D) def;
            }
        }
        return null;
    }

    private <T extends ItemDefinition> boolean isItemValid(ItemDefinition def, QName name, Class<T> clazz) {
    	if (def == null) {
    		return false;
    	}
    	return def.isValidFor(name, clazz);
    }

    public <T extends ItemDefinition> T findItemDefinition(ItemPath path, Class<T> clazz) {
    	while (!path.isEmpty() && !(path.first() instanceof NameItemPathSegment)) {
    		path = path.rest();
    	}
        if (path.isEmpty()) {
            return (T) this;
        }
        QName firstName = ((NameItemPathSegment)path.first()).getName();
        for (ItemDefinition def : getDefinitions()) {
            if (firstName.equals(def.getName())) {
                return def.findItemDefinition(path.rest(), clazz);
            }
        }
        return null;
    }

    public ItemDefinition findItemDefinition(QName name) {
        return findItemDefinition(name, ItemDefinition.class);
    }
    
    public ItemDefinition findItemDefinition(ItemPath path) {
        return findItemDefinition(path, ItemDefinition.class);
    }

    /**
     * Finds a PropertyDefinition by looking at the property name.
     * <p/>
     * Returns null if nothing is found.
     *
     * @param name property definition name
     * @return found property definition or null
     */
    public PrismPropertyDefinition findPropertyDefinition(QName name) {
        return findItemDefinition(name, PrismPropertyDefinition.class);
    }

    public PrismPropertyDefinition findPropertyDefinition(ItemPath path) {
        while (!path.isEmpty() && !(path.first() instanceof NameItemPathSegment)) {
    		path = path.rest();
    	}
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Property path is empty while searching for property definition in " + this);
        }
        QName firstName = ((NameItemPathSegment)path.first()).getName();
        if (path.size() == 1) {
            return findPropertyDefinition(firstName);
        }
        PrismContainerDefinition pcd = findContainerDefinition(firstName);
        if (pcd == null) {
            throw new IllegalArgumentException("There is no " + firstName + " subcontainer in " + this);
        }
        return pcd.findPropertyDefinition(path.rest());
    }

    public PrismReferenceDefinition findReferenceDefinition(QName name) {
        return findItemDefinition(name, PrismReferenceDefinition.class);
    }
    
    /**
     * Finds an inner PropertyContainerDefinition by looking at the property container name.
     * <p/>
     * Returns null if nothing is found.
     *
     * @param name property container definition name
     * @return found property container definition or null
     */
    public <X extends Containerable> PrismContainerDefinition<X> findContainerDefinition(QName name) {
        return findItemDefinition(name, PrismContainerDefinition.class);
    }

    public <X extends Containerable> PrismContainerDefinition<X> findContainerDefinition(String name) {
        return findContainerDefinition(new QName(getNamespace(), name));
    }
    
    /**
     * Finds an inner PropertyContainerDefinition by following the property container path.
     * <p/>
     * Returns null if nothing is found.
     *
     * @param path property container path
     * @return found property container definition or null
     */
    public PrismContainerDefinition findContainerDefinition(ItemPath path) {
        return findItemDefinition(path, PrismContainerDefinition.class);
    }

    /**
     * Returns set of property definitions.
     * <p/>
     * WARNING: This may return definitions from the associated complex type.
     * Therefore changing the returned set may influence also the complex type definition.
     * <p/>
     * The set contains all property definitions of all types that were parsed.
     * Order of definitions is insignificant.
     *
     * @return set of definitions
     */
    public List<? extends ItemDefinition> getDefinitions() {
        if (complexTypeDefinition == null) {
            // e.g. for xsd:any containers
            // FIXME
            return new ArrayList<ItemDefinition>();
        }
        return complexTypeDefinition.getDefinitions();
    }

    /**
     * Returns set of property definitions.
     * <p/>
     * The set contains all property definitions of all types that were parsed.
     * Order of definitions is insignificant.
     * <p/>
     * The returned set is immutable! All changes may be lost.
     *
     * @return set of definitions
     */
    public List<PrismPropertyDefinition> getPropertyDefinitions() {
    	List<PrismPropertyDefinition> props = new ArrayList<PrismPropertyDefinition>();
        for (ItemDefinition def : complexTypeDefinition.getDefinitions()) {
            if (def instanceof PrismPropertyDefinition) {
                props.add((PrismPropertyDefinition) def);
            }
        }
        return props;
    }
    
    public boolean isRuntimeSchema() {
        return isRuntimeSchema;
    }

    public void setRuntimeSchema(boolean isRuntimeSchema) {
        this.isRuntimeSchema = isRuntimeSchema;
    }

    /**
     * Create property container instance with a default name.
     * <p/>
     * This is a preferred way how to create property container.
     */
    @Override
    public PrismContainer<V> instantiate() {
        return instantiate(getNameOrDefaultName());
    }

    /**
     * Create property container instance with a specified name and element.
     * <p/>
     * This is a preferred way how to create property container.
     */
    @Override
    public PrismContainer<V> instantiate(QName name) {
        return new PrismContainer<V>(name, this, prismContext);
    }

    @Override
	public ItemDelta createEmptyDelta(ItemPath path) {
		return new ContainerDelta(path, this);
	}

	/**
     * Shallow clone
     */
    @Override
    public PrismContainerDefinition<V> clone() {
        PrismContainerDefinition<V> clone = new PrismContainerDefinition<V>(name, complexTypeDefinition, prismContext, compileTimeClass);
        copyDefinitionData(clone);
        return clone;
    }

    protected void copyDefinitionData(PrismContainerDefinition<V> clone) {
        super.copyDefinitionData(clone);
        clone.complexTypeDefinition = this.complexTypeDefinition;
        clone.isRuntimeSchema = this.isRuntimeSchema;
        clone.compileTimeClass = this.compileTimeClass;
    }
    
    public PrismContainerDefinition<V> cloneWithReplacedDefinition(QName itemName, ItemDefinition newDefinition) {
    	PrismContainerDefinition<V> clone = clone();
    	ComplexTypeDefinition originalComplexTypeDefinition = getComplexTypeDefinition();
        ComplexTypeDefinition cloneComplexTypeDefinition = originalComplexTypeDefinition.clone();
        clone.setComplexTypeDefinition(cloneComplexTypeDefinition);
        cloneComplexTypeDefinition.replaceDefinition(itemName, newDefinition);
        return clone;
    }

    /**
     * Creates new instance of property definition and adds it to the container.
     * <p/>
     * This is the preferred method of creating a new definition.
     *
     * @param name     name of the property (element name)
     * @param typeName XSD type of the property
     * @return created property definition
     */
    public PrismPropertyDefinition createPropertyDefinition(QName name, QName typeName) {
        PrismPropertyDefinition propDef = new PrismPropertyDefinition(name, name, typeName, prismContext);
        addDefinition(propDef);
        return propDef;
    }
    
    private void addDefinition(ItemDefinition itemDef) {
    	((Collection)getDefinitions()).add(itemDef);
    }

    /**
     * Creates new instance of property definition and adds it to the container.
     * <p/>
     * This is the preferred method of creating a new definition.
     *
     * @param name      name of the property (element name)
     * @param typeName  XSD type of the property
     * @param minOccurs minimal number of occurrences
     * @param maxOccurs maximal number of occurrences (-1 means unbounded)
     * @return created property definition
     */
    public PrismPropertyDefinition createPropertyDefinition(QName name, QName typeName,
            int minOccurs, int maxOccurs) {
        PrismPropertyDefinition propDef = new PrismPropertyDefinition(name, name, typeName, prismContext);
        propDef.setMinOccurs(minOccurs);
        propDef.setMaxOccurs(maxOccurs);
        addDefinition(propDef);
        return propDef;
    }

    // Creates reference to other schema
    // TODO: maybe check if the name is in different namespace
    // TODO: maybe create entirely new concept of property reference?
    public PrismPropertyDefinition createPropertyDefinition(QName name) {
        PrismPropertyDefinition propDef = new PrismPropertyDefinition(name, name, null, prismContext);
        addDefinition(propDef);
        return propDef;
    }

    /**
     * Creates new instance of property definition and adds it to the container.
     * <p/>
     * This is the preferred method of creating a new definition.
     *
     * @param localName name of the property (element name) relative to the schema namespace
     * @param typeName  XSD type of the property
     * @return created property definition
     */
    public PrismPropertyDefinition createPropertyDefinition(String localName, QName typeName) {
        QName name = new QName(getSchemaNamespace(), localName);
        return createPropertyDefinition(name, typeName);
    }

    /**
     * Creates new instance of property definition and adds it to the container.
     * <p/>
     * This is the preferred method of creating a new definition.
     *
     * @param localName     name of the property (element name) relative to the schema namespace
     * @param localTypeName XSD type of the property
     * @return created property definition
     */
    public PrismPropertyDefinition createPropertyDefinition(String localName, String localTypeName) {
        QName name = new QName(getSchemaNamespace(), localName);
        QName typeName = new QName(getSchemaNamespace(), localTypeName);
        return createPropertyDefinition(name, typeName);
    }

    /**
     * Creates new instance of property definition and adds it to the container.
     * <p/>
     * This is the preferred method of creating a new definition.
     *
     * @param localName     name of the property (element name) relative to the schema namespace
     * @param localTypeName XSD type of the property
     * @param minOccurs     minimal number of occurrences
     * @param maxOccurs     maximal number of occurrences (-1 means unbounded)
     * @return created property definition
     */
    public PrismPropertyDefinition createPropertyDefinition(String localName, String localTypeName,
            int minOccurs, int maxOccurs) {
        QName name = new QName(getSchemaNamespace(), localName);
        QName typeName = new QName(getSchemaNamespace(), localTypeName);
        PrismPropertyDefinition propertyDefinition = createPropertyDefinition(name, typeName);
        propertyDefinition.setMinOccurs(minOccurs);
        propertyDefinition.setMaxOccurs(maxOccurs);
        return propertyDefinition;
    }
    
    public PrismContainerDefinition createContainerDefinition(QName name, QName typeName) {
    	return createContainerDefinition(name, typeName, 1, 1);
    }
    
    public PrismContainerDefinition createContainerDefinition(QName name, QName typeName,
            int minOccurs, int maxOccurs) {
    	PrismSchema typeSchema = prismContext.getSchemaRegistry().findSchemaByNamespace(typeName.getNamespaceURI());
    	if (typeSchema == null) {
    		throw new IllegalArgumentException("Schema for namespace "+typeName.getNamespaceURI()+" is not known in the prism context");
    	}
    	ComplexTypeDefinition typeDefinition = typeSchema.findComplexTypeDefinition(typeName);
    	if (typeDefinition == null) {
    		throw new IllegalArgumentException("Type "+typeName+" is not known in the schema");
    	}
    	return createContainerDefinition(name, typeDefinition, minOccurs, maxOccurs);
    }
    
    public PrismContainerDefinition<V> createContainerDefinition(QName name, ComplexTypeDefinition complexTypeDefinition,
            int minOccurs, int maxOccurs) {
    	PrismContainerDefinition<V> def = new PrismContainerDefinition<V>(name, complexTypeDefinition, prismContext);
        def.setMinOccurs(minOccurs);
        def.setMaxOccurs(maxOccurs);
        addDefinition(def);
        return def;
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(DebugDumpable.INDENT_STRING);
        }
        sb.append(toString());
        if (isRuntimeSchema()) {
            sb.append(" dynamic");
        }
        for (Definition def : getDefinitions()) {
        	sb.append("\n");
            sb.append(def.debugDump(indent + 1));
        }
        return sb.toString();
    }


    public boolean isEmpty() {
        return complexTypeDefinition.isEmpty();
    }
    
    /**
     * Return a human readable name of this class suitable for logs.
     */
    @Override
    protected String getDebugDumpClassName() {
        return "PCD";
    }

	@Override
	protected void extendToString(StringBuilder sb) {
		super.extendToString(sb);
		if (isRuntimeSchema) {
			sb.append(",runtime");
		}
	}
    
    

}
