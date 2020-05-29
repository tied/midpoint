package com.evolveum.axiom.api.stream;

import com.evolveum.axiom.api.AxiomName;
import com.evolveum.axiom.concepts.SourceLocation;
import com.evolveum.axiom.lang.spi.AxiomIdentifierResolver;

public interface AxiomItemStream {

    interface Target {
        void startItem(AxiomName item, SourceLocation loc);
        void startValue(Object value, SourceLocation loc);
        void endValue(SourceLocation loc);
        void endItem(SourceLocation loc);
    }

    interface TargetWithResolver extends Target {

        AxiomIdentifierResolver itemResolver();
        AxiomIdentifierResolver valueResolver();

    }

}
