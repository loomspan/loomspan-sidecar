{
    admin off
    auto_https off
}

https://:8443 {
    tls /tls/app-server.pem /tls/app-server-key.pem {
        client_auth {
            mode require_and_verify
            trust_pool file /tls/group-ca.pem
        }
    }
    @callbacks path /loomspan/callbacks/*
    handle @callbacks {
        reverse_proxy 127.0.0.1:8081 {
            lb_try_duration 0
        }
    }
    handle {
        respond 404
    }
}

https://:8444 {
    tls /tls/app-server.pem /tls/app-server-key.pem
    @callbacks path /loomspan/callbacks/*
    handle @callbacks {
        respond 404
    }
    handle {
        reverse_proxy 127.0.0.1:8081 {
            lb_try_duration 0
        }
    }
}

http://:9080 {
    bind 127.0.0.1
    reverse_proxy https://sidecar-proxy:8443 {
        header_up Host sidecar-proxy:8443
        lb_try_duration 0
        transport http {
            tls_trust_pool file /tls/group-ca.pem
            tls_client_auth /tls/app-client.pem /tls/app-client-key.pem
            tls_server_name sidecar-proxy
        }
    }
}
