/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.object;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType.*;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.qmodel.assignment.QAssignmentMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QObjectReferenceMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;

/**
 * Mapping between {@link QAssignmentHolder} and {@link AssignmentHolderType}.
 *
 * @param <S> schema type for the assignment holder object
 * @param <Q> type of entity path
 * @param <R> row type related to the {@link Q}
 */
public class QAssignmentHolderMapping<
        S extends AssignmentHolderType, Q extends QAssignmentHolder<R>, R extends MObject>
        extends QObjectMapping<S, Q, R> {

    public static final String DEFAULT_ALIAS_NAME = "ah";

    public static QAssignmentHolderMapping<?, ?, ?> init(@NotNull SqaleRepoContext repositoryContext) {
        return new QAssignmentHolderMapping<>(QAssignmentHolder.TABLE_NAME, DEFAULT_ALIAS_NAME,
                AssignmentHolderType.class, QAssignmentHolder.CLASS,
                repositoryContext);
    }

    protected QAssignmentHolderMapping(
            @NotNull String tableName,
            @NotNull String defaultAliasName,
            @NotNull Class<S> schemaType,
            @NotNull Class<Q> queryType,
            @NotNull SqaleRepoContext repositoryContext) {
        super(tableName, defaultAliasName, schemaType, queryType, repositoryContext);

        addContainerTableMapping(AssignmentHolderType.F_ASSIGNMENT,
                QAssignmentMapping.initAssignment(repositoryContext),
                joinOn((o, a) -> o.oid.eq(a.ownerOid)));

        addRefMapping(F_ARCHETYPE_REF, QObjectReferenceMapping.initForArchetype(repositoryContext));
        addRefMapping(F_DELEGATED_REF, QObjectReferenceMapping.initForDelegated(repositoryContext));
        addRefMapping(F_ROLE_MEMBERSHIP_REF,
                QObjectReferenceMapping.initForRoleMembership(repositoryContext));
    }

    @Override
    protected Q newAliasInstance(String alias) {
        //noinspection unchecked
        return (Q) new QAssignmentHolder<>(MObject.class, alias);
    }

    @SuppressWarnings("DuplicatedCode") // activation code duplicated with assignment
    @Override
    public @NotNull R toRowObjectWithoutFullObject(S schemaObject, JdbcSession jdbcSession) {
        R row = super.toRowObjectWithoutFullObject(schemaObject, jdbcSession);

        // TODO
        return row;
    }

    @Override
    public void storeRelatedEntities(
            @NotNull R row, @NotNull S schemaObject, @NotNull JdbcSession jdbcSession) {
        super.storeRelatedEntities(row, schemaObject, jdbcSession);

        List<AssignmentType> assignments = schemaObject.getAssignment();
        if (!assignments.isEmpty()) {
            assignments.forEach(assignment ->
                    QAssignmentMapping.getAssignment().insert(assignment, row, jdbcSession));
        }

        storeRefs(row, schemaObject.getArchetypeRef(),
                QObjectReferenceMapping.getForArchetype(), jdbcSession);
        storeRefs(row, schemaObject.getDelegatedRef(),
                QObjectReferenceMapping.getForDelegated(), jdbcSession);
        storeRefs(row, schemaObject.getRoleMembershipRef(),
                QObjectReferenceMapping.getForRoleMembership(), jdbcSession);
    }
}
