package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.CryptographicOperationException;
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

import java.util.List;
import java.util.UUID;

public interface CryptographicOperationService {
    /**
     * List Cipher Attributes
     *
     * @param uuid      UUID of the cryptographic key
     * @param algorithm Algorithm for which the attributes have to be fetched {@Link CryptographicAlgorithm}
     * @return List of attributes for Cipher Attributes
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listCipherAttributes(UUID uuid, CryptographicAlgorithm algorithm) throws ConnectorException;

    /**
     * @param uuid    UUID of the cryptographic key
     * @param request DTO containing the data to encrypt the data
     * @return Encrypted data response {@Link EncryptDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    EncryptDataResponseDto encryptData(UUID uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException;

    /**
     * @param uuid    UUID of the cryptographic key
     * @param request DTO containing the data to decrypt the data
     * @return Decrypted data response {@Link DecryptDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    DecryptDataResponseDto decryptData(UUID uuid, CipherDataRequestDto request) throws ConnectorException, CryptographicOperationException;

    /**
     * @param uuid      UUID of the cryptographic key
     * @param algorithm Algorithm for which the Signature Attributes has to be fetched {@Link CryptographicAlgorithm}
     * @return List of attributes for the Signature Algorithm
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listSignatureAttributes(UUID uuid, CryptographicAlgorithm algorithm) throws ConnectorException;

    /**
     * @param uuid    UUID of the cryptographic key
     * @param request DTO containing the data to sign a request {@Link SignDataRequestDto}
     * @return Signed Data {@Link SignDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    SignDataResponseDto signData(UUID uuid, SignDataRequestDto request) throws ConnectorException, CryptographicOperationException;

    /**
     * @param uuid    UUID of the cryptographic key
     * @param request DTO Containing the data to verify the signature {@Link VerifyDataRequestDto}
     * @return Verification result {@Link VerifyDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    VerifyDataResponseDto verifyData(UUID uuid, VerifyDataRequestDto request) throws ConnectorException, CryptographicOperationException;

    /**
     * @param uuid UUID of the cryptographic key
     * @return List of attributes for random data generation
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listRandomAttributes(UUID uuid) throws ConnectorException;

    /**
     * @param uuid    UUID of the cryptographic key
     * @param request DTO containing the information for generating a strong random data {@Link RandomDataRequestDto}
     * @return Random generated data {@Link RandomDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    RandomDataResponseDto randomData(UUID uuid, RandomDataRequestDto request) throws ConnectorException, CryptographicOperationException;
}
