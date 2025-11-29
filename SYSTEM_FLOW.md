# System Flow Analysis: WannaW1n Banking App (eKYC + ZKP)

Based on the analysis of `README.md` and `ZKP_TECHNICAL.md`, here is the system flow diagram describing the Enrollment and Verification processes.

## System Architecture Overview

The system consists of two main entities:
1.  **Mobile Client (Prover)**: Handles OCR, Biometrics, ZKP generation, and PII encryption.
2.  **Server (Verifier)**: Handles ZKP verification, uniqueness checks, and encrypted storage.

## Flow Diagram

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant App as Mobile App (Client)
    participant Keystore as Android Keystore
    participant Server as Backend Server (Verifier)
    participant DB as Database

    note over User, DB: === ENROLLMENT PHASE (First Time) ===

    User->>App: 1. Capture ID Card Photo
    App->>App: 2. OCR Extraction (ML Kit)<br/>Extract: ID, Name, DOB, Address
    App-->>User: 3. Confirm Extracted Data
    User->>App: 4. Confirm Data
    
    User->>App: 5. Face Scan (Selfie)
    App->>App: 6. Liveness Detection
    alt Liveness Failed
        App-->>User: Show Error (Spoof Detected)
    else Liveness Passed
        App->>Keystore: 7. Generate KeyPair (secp256k1)
        Keystore-->>App: Return PublicKey (PrivateKey stays secure)
        
        App->>App: 8. Encrypt PII (AES-256-GCM)<br/>Hash ID Number (SHA-256)
        
        App->>Keystore: 9. Request Signing/Proof Gen
        Keystore->>Keystore: 10. Generate Schnorr Proof<br/>Commitment = Hash(Pub || ID_Hash)
        Keystore-->>App: Return Proof (R, c, s)
        
        App->>Server: 11. Send Enrollment Payload<br/>{PublicKey, EncryptedPII, ID_Hash, Proof}
        
        Server->>Server: 12. Verify ID Uniqueness (Hash check)
        Server->>Server: 13. Verify Schnorr Proof
        
        alt Verification Failed
            Server-->>App: Error (Invalid Proof / Duplicate ID)
        else Verification Success
            Server->>DB: 14. Store Encrypted Data & PublicKey
            Server-->>App: Success (Account Created)
            App-->>User: Enrollment Complete
        end
    end

    note over User, DB: === VERIFICATION PHASE (Login) ===

    User->>App: 1. Request Login
    App->>Server: 2. Request Challenge
    Server->>App: 3. Send SessionID (Nonce)
    
    App-->>User: 4. Prompt Face Scan (Biometric Unlock)
    User->>App: 5. Scan Face
    
    App->>Keystore: 6. Unlock Private Key
    alt Biometric Mismatch
        Keystore-->>App: Error (Auth Failed)
        App-->>User: Login Failed
    else Biometric Match
        App->>Keystore: 7. Generate Verification Proof
        Keystore->>Keystore: 8. Compute Nullifier = Hash(PrivKey + SessionID)<br/>Generate Schnorr Proof for SessionID
        Keystore-->>App: Return Proof & Nullifier
        
        App->>Server: 9. Send Verification Payload<br/>{PublicKey, Proof, Nullifier, SessionID}
        
        Server->>DB: 10. Check Nullifier (Replay Attack Check)
        alt Nullifier Used
            Server-->>App: Error (Replay Detected)
        else Nullifier Fresh
            Server->>Server: 11. Verify Schnorr Proof
            
            alt Proof Invalid
                Server-->>App: Error (Invalid Proof)
            else Proof Valid
                Server->>DB: 12. Mark Nullifier as Used
                Server->>Server: 13. Generate Session Token
                Server-->>App: Success (Login Approved)
                App-->>User: Access Granted
            end
        end
    end
```

## Key Security Mechanisms

1.  **Zero-Knowledge Property**: The Server verifies the user's identity (possession of Private Key) without ever seeing the Private Key.
2.  **Biometric Binding**: The Private Key is locked in the Android Keystore and only released (for usage, not export) upon successful biometric authentication.
3.  **Replay Protection**: The `Nullifier` ensures that a valid proof for one session cannot be reused for another.
4.  **Data Privacy**: PII is encrypted on the client side. The server only holds encrypted blobs and hashes.
