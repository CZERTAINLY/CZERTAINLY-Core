package com.czertainly.core.model.cbom;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Setter
@Getter
@Schema(description = "Cryptographic assets breakdown")
public class CryptoAssetsDto {

    @Schema(
            description = "Total number of cryptographic assets",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer total;

    @NotNull
    @Valid
    @Schema(
            description = "Certificate statistics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private CertificateStats certificates;

    @NotNull
    @Valid
    @Schema(
            description = "Key statistics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private KeyStats keys;

    @NotNull
    @Valid
    @Schema(
            description = "Hash function statistics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private HashFunctionStats hashFunctions;

    @NotNull
    @Valid
    @Schema(
            description = "Signature scheme statistics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private SignatureSchemeStats signatureSchemes;

    @NotNull
    @Valid
    @Schema(
            description = "Key agreement statistics",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private KeyAgreementStats keyAgreement;

    @Setter
    @Getter
    @Schema(
            description = "Certificate statistics"
    )
    public static class CertificateStats {
        @Schema(
                description = "Total number of certificates",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer total;

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("total", total)
                    .toString();
        }
    }

    @Setter
    @Getter
    @Schema(
            description = "Key statistics"
    )
    public static class KeyStats {
        @Schema(
                description = "Total number of keys",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer total;

        @Schema(
                description = "Number of symmetric keys",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer symmetric;

        @Schema(
                description = "Number of asymmetric keys",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer asymmetric;

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("total", total)
                    .append("symmetric", symmetric)
                    .append("asymmetric", asymmetric)
                    .toString();
        }
    }

    @Setter
    @Getter
    @Schema(
            description = "Hash function statistics"
    )
    public static class HashFunctionStats {
        @Schema(
            description = "Total number of hash functions",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer total;

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("total", total)
                    .toString();
        }
    }

    @Setter
    @Getter
    @Schema(
        description = "Signature scheme statistics"
    )
    public static class SignatureSchemeStats {
        @Schema(
            description = "Number of signature schemes",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer signatureSchemes;

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("signatureSchemes", signatureSchemes)
                    .toString();
        }
    }

    @Setter
    @Getter
    @Schema(
            description = "Key agreement statistics"
    )
    public static class KeyAgreementStats {
        @Schema(
                description = "Number of key agreements",
                example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        private Integer keyAgreements;

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("keyAgreements", keyAgreements)
                    .toString();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("total", total)
        .append("certificates", certificates)
        .append("keys", keys)
        .append("hashFunctions", hashFunctions)
        .append("signatureSchemes", signatureSchemes)
        .append("keyAgreement", keyAgreement)
        .toString();
    }
}
