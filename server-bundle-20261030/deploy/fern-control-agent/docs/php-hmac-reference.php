<?php
// Reference only. Keep the shared secret in the private config outside public_html.
function fernAgentHeaders(string $secret, string $method, string $path, string $body = ''): array {
    $timestamp = (string) time();
    $nonce = rtrim(strtr(base64_encode(random_bytes(24)), '+/', '-_'), '=');
    $canonical = $timestamp.'.'.$nonce.'.'.strtoupper($method).'.'.$path.'.'.$body;
    $signature = hash_hmac('sha256', $canonical, $secret);
    return [
        'Content-Type: application/json',
        'X-Fern-Timestamp: '.$timestamp,
        'X-Fern-Nonce: '.$nonce,
        'X-Fern-Signature: '.$signature,
    ];
}
