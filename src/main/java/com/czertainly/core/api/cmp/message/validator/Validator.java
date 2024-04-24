package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;

/**
 * Common interface for (single-way) validation
 *
 * @param <I> subject of validation
 * @param <E> return type of validation
 */
public interface Validator<I,E> {

    /**
     * validate given <code>subject</code>
     * @param subject for validation
     * @return result of validation
     * @throws CmpException if validation has failed
     */
    E validate(I subject) throws CmpException;

}
