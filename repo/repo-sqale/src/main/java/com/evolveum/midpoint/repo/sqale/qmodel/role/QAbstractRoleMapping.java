/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel.role;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType.*;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.qmodel.assignment.QAssignmentMapping;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QFocusMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AutoassignSpecificationType;

/**
 * Mapping between {@link QAbstractRole} and {@link AbstractRoleType}.
 *
 * @param <S> schema type for the abstract role object
 * @param <Q> type of entity path
 * @param <R> row type related to the {@link Q}
 */
public class QAbstractRoleMapping<
        S extends AbstractRoleType, Q extends QAbstractRole<R>, R extends MAbstractRole>
        extends QFocusMapping<S, Q, R> {

    public static final String DEFAULT_ALIAS_NAME = "ar";

    public static QAbstractRoleMapping<?, ?, ?> init(@NotNull SqaleRepoContext repositoryContext) {
        return new QAbstractRoleMapping<>(QAbstractRole.TABLE_NAME, DEFAULT_ALIAS_NAME,
                AbstractRoleType.class, QAbstractRole.CLASS, repositoryContext);
    }

    protected QAbstractRoleMapping(
            @NotNull String tableName,
            @NotNull String defaultAliasName,
            @NotNull Class<S> schemaType,
            @NotNull Class<Q> queryType,
            @NotNull SqaleRepoContext repositoryContext) {
        super(tableName, defaultAliasName, schemaType, queryType, repositoryContext);

        addNestedMapping(F_AUTOASSIGN, AutoassignSpecificationType.class)
                .addItemMapping(AutoassignSpecificationType.F_ENABLED,
                        booleanMapper(q -> q.autoAssignEnabled));
        addItemMapping(F_DISPLAY_NAME,
                polyStringMapper(q -> q.displayNameOrig, q -> q.displayNameNorm));
        addItemMapping(F_IDENTIFIER, stringMapper(q -> q.identifier));
        addItemMapping(F_REQUESTABLE, booleanMapper(q -> q.requestable));
        addItemMapping(F_RISK_LEVEL, stringMapper(q -> q.riskLevel));

        addContainerTableMapping(F_INDUCEMENT,
                QAssignmentMapping.initInducement(repositoryContext),
                joinOn((o, a) -> o.oid.eq(a.ownerOid)));
    }

    @Override
    protected Q newAliasInstance(String alias) {
        //noinspection unchecked
        return (Q) new QAbstractRole<>(MAbstractRole.class, alias);
    }

    @Override
    public @NotNull R toRowObjectWithoutFullObject(S abstractRole, JdbcSession jdbcSession) {
        R row = super.toRowObjectWithoutFullObject(abstractRole, jdbcSession);

        AutoassignSpecificationType autoassign = abstractRole.getAutoassign();
        if (autoassign != null) {
            row.autoAssignEnabled = autoassign.isEnabled();
        }
        setPolyString(abstractRole.getDisplayName(),
                o -> row.displayNameOrig = o, n -> row.displayNameNorm = n);
        row.identifier = abstractRole.getIdentifier();
        row.requestable = abstractRole.isRequestable();
        row.riskLevel = abstractRole.getRiskLevel();
        return row;
    }

    @Override
    public void storeRelatedEntities(
            @NotNull R row, @NotNull S schemaObject, @NotNull JdbcSession jdbcSession) {
        super.storeRelatedEntities(row, schemaObject, jdbcSession);

        List<AssignmentType> inducement = schemaObject.getInducement();
        if (!inducement.isEmpty()) {
            inducement.forEach(assignment ->
                    QAssignmentMapping.getInducement().insert(assignment, row, jdbcSession));
        }
    }
}
