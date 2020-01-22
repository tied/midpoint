/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2014.02.04 at 01:34:24 PM CET
//


package com.evolveum.prism.xml.ns._public.types_3;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import com.evolveum.midpoint.prism.JaxbVisitor;
import org.apache.commons.lang.ArrayUtils;


/**
 *
 *                 Specific subtype for protected binary byte array data.
 *
 *
 * <p>Java class for ProtectedByteArrayType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="ProtectedByteArrayType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://prism.evolveum.com/xml/ns/public/types-3}ProtectedDataType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="clearValue" type="{http://www.w3.org/2001/XMLSchema}base64Binary" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ProtectedByteArrayType")
public class ProtectedByteArrayType extends ProtectedDataType<Byte[]> {

    @Override
    public byte[] getClearBytes() {
        return ArrayUtils.toPrimitive(getClearValue());
    }

    @Override
    public void setClearBytes(byte[] bytes) {
        setClearValue(ArrayUtils.toObject(bytes));
    }

    @Override
    public boolean canSupportType(Class<?> type) {
        return byte[].class.isAssignableFrom(type);
    }

    @Override
    public void accept(JaxbVisitor visitor) {
        visitor.visit(this);
    }
}
