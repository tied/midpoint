/**
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
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.valueconstruction.ValueConstruction;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.MiscUtil;

/**
 * @author semancik
 *
 */
public class DeltaSetTriple<T> implements Dumpable {
	
	Collection<T> zeroSet;
	Collection<T> plusSet;
	Collection<T> minusSet;
	
	public DeltaSetTriple() {
		zeroSet = createSet();
		plusSet = createSet();
		minusSet = createSet();
	}

	public DeltaSetTriple(Collection<T> zeroSet, Collection<T> plusSet, Collection<T> minusSet) {
		this.zeroSet = zeroSet;
		this.plusSet = plusSet;
		this.minusSet = minusSet;
	}

	public static <T> DeltaSetTriple<T> diff(Collection<T> valuesOld, Collection<T> valuesNew) {
		DeltaSetTriple<T> triple = new DeltaSetTriple<T>();
		for (T val: valuesOld) {
			if (valuesNew.contains(val)) {
				triple.getZeroSet().add(val);
			} else {
				triple.getMinusSet().add(val);
			}
		}
		for (T val: valuesNew) {
			if (!valuesOld.contains(val)) {
				triple.getPlusSet().add(val);
			}
		}
		return triple;
	}

	private Collection<T> createSet() {
		return new HashSet<T>();
	}

	public Collection<T> getZeroSet() {
		return zeroSet;
	}
	public Collection<T> getPlusSet() {
		return plusSet;
	}
	public Collection<T> getMinusSet() {
		return minusSet;
	}
	
	public Collection<T> union() {
		return MiscUtil.union(zeroSet, plusSet, minusSet);
	}
	
	public Collection<T> getNonNegativeValues() {
		return MiscUtil.union(zeroSet, plusSet);
	}
	
	public <O> void distributeAs(T myMember, DeltaSetTriple<O> otherTriple, O otherMember) {
		if (otherTriple.getZeroSet() != null && otherTriple.getZeroSet().contains(otherMember)) {
			zeroSet.add(myMember);
		}
		if (otherTriple.getPlusSet() != null && otherTriple.getPlusSet().contains(otherMember)) {
			plusSet.add(myMember);
		}
		if (otherTriple.getMinusSet() != null && otherTriple.getMinusSet().contains(otherMember)) {
			minusSet.add(myMember);
		}
	}

	@Override
	public String toString() {
		return dump();
	}
	
	@Override
	public String dump() {
		StringBuilder sb = new StringBuilder();
		sb.append("DeltaSetTriple(");
		dumpSet(sb,"zero",zeroSet);
		dumpSet(sb,"plus",plusSet);
		dumpSet(sb,"minus",minusSet);
		sb.append(")");
		return sb.toString();
	}

	private void dumpSet(StringBuilder sb, String label, Collection<T> set) {
		sb.append(label).append(": ").append(set).append("; ");
	}
	
}
