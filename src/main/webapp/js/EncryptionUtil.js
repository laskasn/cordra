(function(){
"use strict";

var window = window || self;
window.cnri = window.cnri || {};
cnri.util = cnri.util || {};

var EncryptionUtil = cnri.util.EncryptionUtil = {};
    
EncryptionUtil.KEY_ENCODING_DSA_PRIVATE    = cnri.util.Encoder.Utf8.bytes("DSA_PRIV_KEY");
EncryptionUtil.KEY_ENCODING_RSA_PRIVATE    = cnri.util.Encoder.Utf8.bytes("RSA_PRIV_KEY");
EncryptionUtil.KEY_ENCODING_RSACRT_PRIVATE = cnri.util.Encoder.Utf8.bytes("RSA_PRIVCRT_KEY");

EncryptionUtil.KEY_ENCODING_DSA_PUBLIC     = cnri.util.Encoder.Utf8.bytes("DSA_PUB_KEY");
EncryptionUtil.KEY_ENCODING_RSA_PUBLIC     = cnri.util.Encoder.Utf8.bytes("RSA_PUB_KEY");
EncryptionUtil.KEY_ENCODING_DH_PUBLIC      = cnri.util.Encoder.Utf8.bytes("DH_PUB_KEY");

EncryptionUtil.DEFAULT_E = new Uint8Array([1, 0, 1]);

EncryptionUtil.ENCRYPT_DES_ECB_PKCS5 = 0;   // DES with ECB and PKCS5 padding
EncryptionUtil.ENCRYPT_NONE          = 1;   // no encryption
EncryptionUtil.ENCRYPT_DES_CBC_PKCS5 = 2;   // DES with CBC and PKCS5 padding
EncryptionUtil.ENCRYPT_AES_CBC_PKCS5 = 4;   // AES with CBC and PKCS5 padding

EncryptionUtil.getPrivateKeyFromBytes = function (pkBuf, offset) {
    if (!offset) offset = 0;
    var privateKey = {};
    var view = new DataView(pkBuf.buffer, pkBuf.byteOffset, pkBuf.byteLength);
    var bytesAndOffset = readBytes(view, offset);
    var keyType = bytesAndOffset.bytes;

    if (compareArrays(keyType, EncryptionUtil.KEY_ENCODING_DSA_PRIVATE)) {
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var x = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var p = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var q = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var g = bytesAndOffset.bytes;
//        privateKey.x = x;
//        privateKey.p = p;
//        privateKey.q = q;
//        privateKey.g = g;
        privateKey.x = cnri.util.Encoder.Base64Url.string(unsigned(x));
        privateKey.p = cnri.util.Encoder.Base64Url.string(unsigned(p));
        privateKey.q = cnri.util.Encoder.Base64Url.string(unsigned(q));
        privateKey.g = cnri.util.Encoder.Base64Url.string(unsigned(g));
        privateKey.kty = "DSA";
    } else if (compareArrays(keyType, EncryptionUtil.KEY_ENCODING_RSA_PRIVATE)) {
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var m = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var exp = readBytes(view, bytesAndOffset.offset);
//        privateKey.n = m;
//        privateKey.d = exp;
//        privateKey.e = EncryptionUtil.DEFAULT_E;
        privateKey.n = cnri.util.Encoder.Base64Url.string(unsigned(m));
        privateKey.d = cnri.util.Encoder.Base64Url.string(unsigned(exp));
        privateKey.e = cnri.util.Encoder.Base64Url.string(EncryptionUtil.DEFAULT_E);        
        privateKey.kty = "RSA";
    } else if (compareArrays(keyType, EncryptionUtil.KEY_ENCODING_RSACRT_PRIVATE)) { 
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var n = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var pubEx = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var ex = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var p = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var q = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var exP = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var exQ = bytesAndOffset.bytes;   
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var coeff = bytesAndOffset.bytes;     
//        privateKey.n = n;
//        privateKey.d = ex;
//        privateKey.e = pubEx;
        privateKey.n = cnri.util.Encoder.Base64Url.string(unsigned(n));
        privateKey.d = cnri.util.Encoder.Base64Url.string(unsigned(ex));
        privateKey.e = cnri.util.Encoder.Base64Url.string(unsigned(pubEx));
        
        privateKey.p = cnri.util.Encoder.Base64Url.string(unsigned(p));
        privateKey.q = cnri.util.Encoder.Base64Url.string(unsigned(q));
        privateKey.dp = cnri.util.Encoder.Base64Url.string(unsigned(exP));
        privateKey.dq = cnri.util.Encoder.Base64Url.string(unsigned(exQ));
        privateKey.qi = cnri.util.Encoder.Base64Url.string(unsigned(coeff));
        privateKey.kty = "RSA";
    } 
    return privateKey;
};

EncryptionUtil.getPublicKeyFromBytes = function (pkBuf, offset) {
    if (!offset) offset = 0;
    var publicKey = {};
    var view = new DataView(pkBuf.buffer, pkBuf.byteOffset, pkBuf.byteLength);
    var bytesAndOffset = readBytes(view, offset);
    var keyType = bytesAndOffset.bytes;
    bytesAndOffset.offset = bytesAndOffset.offset + 2; //skip unused flags
    if (compareArrays(keyType, EncryptionUtil.KEY_ENCODING_DSA_PUBLIC)) {
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var q = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var p = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var g = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var y = bytesAndOffset.bytes;
//        publicKey.q = q;
//        publicKey.p = p;
//        publicKey.g = g;
//        publicKey.y = y;
        publicKey.q = cnri.util.Encoder.Base64Url.string(unsigned(q));
        publicKey.p = cnri.util.Encoder.Base64Url.string(unsigned(p));
        publicKey.g = cnri.util.Encoder.Base64Url.string(unsigned(g));
        publicKey.y = cnri.util.Encoder.Base64Url.string(unsigned(y));
        publicKey.kty = "DSA";
    } else if (compareArrays(keyType, EncryptionUtil.KEY_ENCODING_RSA_PUBLIC)) {
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var ex = bytesAndOffset.bytes;
        bytesAndOffset = readBytes(view, bytesAndOffset.offset);
        var m = bytesAndOffset.bytes;

//        publicKey.n = m;
//        publicKey.e = ex;        
        publicKey.n = cnri.util.Encoder.Base64Url.string(unsigned(m));
        publicKey.e = cnri.util.Encoder.Base64Url.string(unsigned(ex));
        publicKey.kty = "RSA";
    } else if (compareArrays(keyType, EncryptionUtil.KEY_ENCODING_DH_PUBLIC)) { 
        throw { name : "EncryptionError", message : "KEY_ENCODING_DH_PUBLIC key type not supported" };
    } 
    return publicKey;
};

EncryptionUtil.requiresSecretKey = function (ciphertext) {
    var encryptionType = readInt(ciphertext, 0);
    if(encryptionType === EncryptionUtil.ENCRYPT_NONE) {
        return false;
    } else {
        return true;
    }
};

//EncryptionUtil.decrypt = function (data, key) {
//    var encryptionType = readInt(data, 0);
//    if (encryptionType == EncryptionUtil.ENCRYPT_NONE) {
//        return EncryptionUtil.stripContainerFromUnencryptedData(data);
//    } else if (encryptionType == EncryptionUtil.ENCRYPT_DES_ECB_PKCS5) {
//        return EncryptionUtil.decryptDesEcb(data, key);
//    } else if (encryptionType == EncryptionUtil.ENCRYPT_DES_CBC_PKCS5) {
//        return EncryptionUtil.decryptDes(data, key);
//    } else if (encryptionType == EncryptionUtil.ENCRYPT_AES_CBC_PKCS5) {
//        return EncryptionUtil.decryptAes(data, key);
//    }
//};

EncryptionUtil.decryptPrivateKeyAes = function (data, passPhrase) {
    var INT_SIZE = 4;
    var offset = 4;
    var view = new DataView(data.buffer, data.byteOffset, data.byteLength);
    var bytesAndOffset = readBytes(view, offset);
    var salt = bytesAndOffset.bytes;
    offset = offset + INT_SIZE + salt.length;

    var iterations = view.getInt32(offset);
    offset = offset + INT_SIZE;
  
    var keyLength = view.getInt32(offset);
    offset = offset + INT_SIZE;
  
    bytesAndOffset = readBytes(view, offset);
    var iv = bytesAndOffset.bytes;
    offset = offset + INT_SIZE + iv.length;
  
    bytesAndOffset = readBytes(view, offset);
    var ciphertext = bytesAndOffset.bytes;
    
    var passPhraseBytes = cnri.util.Encoder.Utf8.bytes(passPhrase);

    return crypto.subtle.importKey('raw', passPhraseBytes, {name: 'PBKDF2'}, false, ['deriveBits', 'deriveKey'])
        .then(function (passPhraseKey) {
            var deriveAlg = { 
                "name": 'PBKDF2',
                "salt": salt,
                "iterations": iterations,
                "hash": 'SHA-1'
            };
            return window.crypto.subtle.deriveKey(deriveAlg, passPhraseKey, { name: 'AES-CBC', length: keyLength }, false, [ 'decrypt' ]);
        }).then(function (keyDerivedFromPassPhrase) {
            var alg = { name: 'AES-CBC', iv: iv };
            return crypto.subtle.decrypt(alg, keyDerivedFromPassPhrase, ciphertext);
        });
};

EncryptionUtil.stripContainerFromUnencryptedData = function (data) {
    return data.subarray(4);
};

function printBytes(bytes) {
    var string = "";
    for (var i = 0; i < bytes.length; i++) {
        var out = 0;
        var b = bytes[i];
        if (b >= 128) {
            out = b -256;
        } else {
            out = b;
        }
        string = string + out + " ";
    }
    console.log(string);
}

function positiveByteArray(uint8Array) {
    var arr = Array.apply([], uint8Array);
    while(arr.length > 1 && arr[0] === 0) arr.shift();
    if(arr[0] >= 0x80) arr.unshift(0);
    return arr;
}

function compareArrays(a, b) {
    if (!b) return false;
    if (a.length !== b.length) return false;
    for (var i = 0; i < a.length; i++) {
        if (a[i] !== b[i]) return false;
    }
    return true;
}
    
function readBytes(view, offset) {
    var len = view.getInt32(offset);
    if(len < 0 || offset + 4 + len  > view.byteLength) throw { name : "HsEncoderError", message : "bad string length" };
    var arr = new Uint8Array(view.buffer, view.byteOffset + offset + 4, len);
    return { offset : offset + 4 + len, bytes : arr };
}

function readInt(buf, offset) { //Note that buf is a Uint8Array not a DataView
    return buf[offset] << 24 | 
    (0x00ff & buf[offset+1]) << 16 |  
    (0x00ff & buf[offset+2]) << 8  |
    (0x00ff & buf[offset+3]);
}

function unsigned(arr) {
    if (arr.length === 0) return new Uint8Array(1);
    var zeros = 0;
    for (var i = 0; i < arr.length; i++) {
        if (arr[i] === 0) zeros++;
        else break;
    }
    if (zeros === arr.length) zeros--;
    if (zeros === 0) return arr;
    return new Uint8Array(arr.buffer, arr.byteOffset + zeros, arr.length - zeros);
}

/*end*/})();
