package com.czertainly.core.service.tsa.timequality;

record TimeReferencePair(long wallTimeMillis, long monotonicNanos, double measuredDriftMs) {
}
