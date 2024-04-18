package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;

/**
 * Common interface for validation
 *
 * @param <I> subject of validation
 * @param <E> return type of validation
 */
public interface Validator<I,E> {

    E validate(I subject) throws CmpException;

}
