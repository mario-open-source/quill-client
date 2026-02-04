package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Represents an SSL certificate
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Certificate {
    private String name;
    private List<String> matches;
    private CertificateFile key;
    private CertificateFile cert;
    private String passphrase;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMatches() {
        return matches;
    }

    public void setMatches(List<String> matches) {
        this.matches = matches;
    }

    public CertificateFile getKey() {
        return key;
    }

    public void setKey(CertificateFile key) {
        this.key = key;
    }

    public CertificateFile getCert() {
        return cert;
    }

    public void setCert(CertificateFile cert) {
        this.cert = cert;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    /**
     * Inner class for certificate file paths
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CertificateFile {
        private String src;

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }
    }
}
