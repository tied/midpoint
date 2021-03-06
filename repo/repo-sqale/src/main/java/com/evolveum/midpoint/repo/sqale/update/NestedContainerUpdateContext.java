/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.update;

import com.querydsl.core.types.Path;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.repo.sqale.qmodel.SqaleNestedMapping;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryModelMapping;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;

/**
 * Update context for nested containers stored in the same table used by the parent context.
 *
 * @param <S> schema type of the container mapped by the nested mapping
 * @param <Q> entity query type that holds the data for the mapped attributes
 * @param <R> row type related to the {@link Q}
 */
public class NestedContainerUpdateContext<S extends Containerable, Q extends FlexibleRelationalPathBase<R>, R>
        extends SqaleUpdateContext<S, Q, R> {

    private final SqaleNestedMapping<S, Q, R> mapping;

    public NestedContainerUpdateContext(
            SqaleUpdateContext<?, Q, R> parentContext,
            SqaleNestedMapping<S, Q, R> mapping) {
        super(parentContext, parentContext.row);

        this.mapping = mapping;
    }

    @Override
    public Q path() {
        //noinspection unchecked
        return (Q) parentContext.path();
    }

    @Override
    public QueryModelMapping<S, Q, R> mapping() {
        return mapping;
    }

    @Override
    public <P extends Path<T>, T> void set(P path, T value) {
        parentContext.set(path, value);
    }

    @Override
    protected void finishExecutionOwn() {
        // nothing to do, parent context has all the updates
    }
}
