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

package com.evolveum.midpoint.repo.sql;

import com.evolveum.midpoint.repo.sql.data.common.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author lazyman
 */
public class ContainerIdGenerator implements IdentifierGenerator {

    private static final Trace LOGGER = TraceManager.getTrace(ContainerIdGenerator.class);

    @Override
    public Serializable generate(SessionImplementor session, Object object) throws HibernateException {
        if (object instanceof RObject) {
            LOGGER.trace("Created id='0' for '{}'.", new Object[]{toString(object)});
            return 0L;
        } else if (object instanceof RContainer) {
            RContainer container = (RContainer) object;
            if (container.getId() != null && container.getId() != 0) {
                LOGGER.trace("Created id='{}' for '{}'.", new Object[]{container.getId(), toString(object)});
                return container.getId();
            }

            if (container instanceof ROwnable) {
                RContainer parent = ((ROwnable) container).getContainerOwner();

                if (parent instanceof RUser) {
                    RUser user = (RUser) parent;

                    Long id = getNextId(user.getAssignments());
                    LOGGER.trace("Created id='{}' for '{}'.", new Object[]{id, toString(object)});
                    return id;
                } else if (parent instanceof RRole) {
                    RRole role = (RRole) parent;

                    Set<RContainer> containers = new HashSet<RContainer>();
                    if (role.getAssignments() != null) {
                        containers.addAll(role.getAssignments());
                    }
                    if (role.getExclusions() != null) {
                        containers.addAll(role.getExclusions());
                    }

                    Long id = getNextId(containers);
                    LOGGER.trace("Created id='{}' for '{}'.", new Object[]{id, toString(object)});
                    return id;
                }
            }
        }

        throw new IllegalStateException("Couldn't create id for '"
                + object.getClass().getSimpleName() + "' (should not happen).");
    }

    private String toString(Object object) {
        RContainer container = (RContainer) object;

        StringBuilder builder = new StringBuilder();
        builder.append(object.getClass().getSimpleName());
        builder.append("[");
        builder.append(container.getOid());
        builder.append(",");
        builder.append(container.getId());
        builder.append("]");

        return builder.toString();
    }

    private Long getNextId(Set<? extends RContainer> set) {
        Long id = 0L;
        if (set != null) {
            for (RContainer container : set) {
                Long contId = container.getId();
                if (contId != null && contId > id) {
                    id = contId;
                }
            }
        }

        return id + 1;
    }
}
