UPDATE certificate SET trusted_ca = false WHERE trusted_ca IS NULL AND issuer_dn_normalized = subject_dn_normalized;
