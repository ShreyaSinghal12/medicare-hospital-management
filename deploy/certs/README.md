Put the TLS certificate and key here as `tls.crt` and `tls.key` (they are git-ignored).
For a local test only, a self-signed pair can be made with:

    openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=localhost" -keyout tls.key -out tls.crt
