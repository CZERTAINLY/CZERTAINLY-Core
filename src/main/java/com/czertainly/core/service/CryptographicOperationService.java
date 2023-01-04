package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.RandomDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.SignDataRequestDto;
import com.czertainly.api.model.client.cryptography.operations.VerifyDataRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.DecryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.EncryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.RandomDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.SignDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.VerifyDataResponseDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;
import java.util.UUID;

public interface CryptographicOperationService {
    /**
     * List Cipher Attributes
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param algorithm         Algorithm for which the attributes have to be fetched {@Link CryptographicAlgorithm}
     * @return List of attributes for Cipher Attributes
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listCipherAttributes(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid,
            CryptographicAlgorithm algorithm
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param request           DTO containing the data to encrypt the data
     * @return Encrypted data response {@Link EncryptDataResponseDto}
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    EncryptDataResponseDto encryptData(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid,
            CipherDataRequestDto request
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param request           DTO containing the data to decrypt the data
     * @return Decrypted data response {@Link DecryptDataResponseDto}
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    DecryptDataResponseDto decryptData(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid,
            CipherDataRequestDto request
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param algorithm         Algorithm for which the Signature Attributes has to be fetched {@Link CryptographicAlgorithm}
     * @return List of attributes for the Signature Algorithm
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listSignatureAttributes(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid,
            CryptographicAlgorithm algorithm
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param request           DTO containing the data to sign a request {@Link SignDataRequestDto}
     * @return Signed Data {@Link SignDataResponseDto}
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    SignDataResponseDto signData(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid, SignDataRequestDto request
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param request           DTO Containing the data to verify the signature {@Link VerifyDataRequestDto}
     * @return Verification result {@Link VerifyDataResponseDto}
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    VerifyDataResponseDto verifyData(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid, VerifyDataRequestDto request
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @return List of attributes for random data generation
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listRandomAttributes(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid
    ) throws ConnectorException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param tokenProfileUUID  UUID of the token profile
     * @param uuid              UUID of the cryptographic key
     * @param request           DTO containing the information for generating a strong random data {@Link RandomDataRequestDto}
     * @return Random generated data {@Link RandomDataResponseDto}
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    RandomDataResponseDto randomData(
            SecuredParentUUID tokenInstanceUuid,
            SecuredUUID tokenProfileUUID,
            UUID uuid,
            RandomDataRequestDto request
    ) throws ConnectorException;
}
