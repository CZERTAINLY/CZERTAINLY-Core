package com.czertainly.core.service;

import com.czertainly.api.exception.CryptographicOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.operations.*;

import java.util.List;

public interface CryptographicOperationService {
    /**
     * List Cipher Attributes
     *
     * @param tokenInstanceUuid UUID of the token instance
     * @param algorithm         Algorithm for which the attributes has to be fetched {@Link CryptographicAlgorithm}
     * @return List of attributes for Cipher Attributes
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listCipherAttributes(String tokenInstanceUuid, CryptographicAlgorithm algorithm) throws NotFoundException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param request           DTO containing the data to encrypt the data
     * @return Encrypted data response {@Link EncryptDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    EncryptDataResponseDto encryptData(String tokenInstanceUuid, CipherDataRequestDto request) throws NotFoundException, CryptographicOperationException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param request           DTO containing the data to decrypt the data
     * @return Decrypted data response {@Link DecryptDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    DecryptDataResponseDto decryptData(String tokenInstanceUuid, CipherDataRequestDto request) throws NotFoundException, CryptographicOperationException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param algorithm         Algorithm for which the Signature Attributes has to be fecthed {@Link CryptographicAlgorithm}
     * @return List of attributes for the Signature Algorithm
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listSignatureAttributes(String tokenInstanceUuid, CryptographicAlgorithm algorithm) throws NotFoundException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param request           DTO containing the data to sign a request {@Link SignDataRequestDto}
     * @return Signed Data {@Link SignDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    SignDataResponseDto signData(String tokenInstanceUuid, SignDataRequestDto request) throws NotFoundException, CryptographicOperationException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param request           DTO Containing the data to verify the signature {@Link VerifyDataRequestDto}
     * @return Verification result {@Link VerifyDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    VerifyDataResponseDto verifyData(String tokenInstanceUuid, VerifyDataRequestDto request) throws NotFoundException, CryptographicOperationException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @return List of attributes for random data generation
     * @throws NotFoundException when the token instance with the specified UUID is not found
     */
    List<BaseAttribute> listRandomAttributes(String tokenInstanceUuid) throws NotFoundException;

    /**
     * @param tokenInstanceUuid UUID of the token instance
     * @param request           DTO containing the information for generating a strong random data {@Link RandomDataRequestDto}
     * @return Random generated data {@Link RandomDataResponseDto}
     * @throws NotFoundException               when the token instance with the specified UUID is not found
     * @throws CryptographicOperationException when there are error related with cryptographic operations {@Link CryptographicOperationException}
     */
    RandomDataResponseDto randomData(String tokenInstanceUuid, RandomDataRequestDto request) throws NotFoundException, CryptographicOperationException;
}
