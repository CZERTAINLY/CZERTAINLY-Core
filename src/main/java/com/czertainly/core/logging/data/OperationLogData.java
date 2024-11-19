package com.czertainly.core.logging.data;

import java.io.Serializable;

public record OperationLogData(
        Serializable request,
        Serializable response
) {
}
