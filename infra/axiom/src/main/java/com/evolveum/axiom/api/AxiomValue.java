package com.evolveum.axiom.api;

import java.util.Map;
import java.util.Optional;

import com.evolveum.axiom.api.schema.AxiomTypeDefinition;


public interface AxiomValue<V> extends AxiomInfraValue {

    AxiomName TYPE = AxiomName.axiom("type");
    AxiomName VALUE = AxiomName.axiom("value");

    Optional<AxiomTypeDefinition> type();

    V value();

    default Optional<AxiomComplexValue> asComplex() {
        if(this instanceof AxiomComplexValue)  {
            return Optional.of((AxiomComplexValue) this);
        }
        return Optional.empty();
    }

    interface Factory<V,T extends AxiomValue<V>> extends AxiomInfraValue.Factory<T> {

        @Override
        default T create(Map<AxiomName, AxiomItem<?>> infraItems) {
            AxiomTypeDefinition type = (AxiomTypeDefinition) infraItems.get(TYPE).onlyValue().value();
            V value = (V) infraItems.get(VALUE).onlyValue().value();
            return create(type, value, infraItems);
        }

        T create(AxiomTypeDefinition type, V value, Map<AxiomName, AxiomItem<?>> infraItems);

    }

}
