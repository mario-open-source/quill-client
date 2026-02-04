package com.quillapiclient.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// Represents authentication information
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Auth {
    public String type;
    public Object noauth;
    // For basic auth credentials
    public List<Credential> basic;
    // For bearer auth credentials
    public List<Credential> bearer;
    // For API key auth
    public List<Credential> apikey;
    // For AWS Signature v4
    public List<Credential> awsv4;
    // For digest auth
    public List<Credential> digest;
    // For EdgeGrid auth
    public List<Credential> edgegrid;
    // For Hawk auth
    public List<Credential> hawk;
    // For NTLM auth
    public List<Credential> ntlm;
    // For OAuth1
    public List<Credential> oauth1;
    // For OAuth2
    public List<Credential> oauth2;

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<Credential> getBasic() { return basic; }
    public void setBasic(List<Credential> basic) { this.basic = basic; }
    public List<Credential> getBearer() { return bearer; }
    public void setBearer(List<Credential> bearer) { this.bearer = bearer; }
    public Object getNoauth() { return noauth; }
    public void setNoauth(Object noauth) { this.noauth = noauth; }
    public List<Credential> getApikey() { return apikey; }
    public void setApikey(List<Credential> apikey) { this.apikey = apikey; }
    public List<Credential> getAwsv4() { return awsv4; }
    public void setAwsv4(List<Credential> awsv4) { this.awsv4 = awsv4; }
    public List<Credential> getDigest() { return digest; }
    public void setDigest(List<Credential> digest) { this.digest = digest; }
    public List<Credential> getEdgegrid() { return edgegrid; }
    public void setEdgegrid(List<Credential> edgegrid) { this.edgegrid = edgegrid; }
    public List<Credential> getHawk() { return hawk; }
    public void setHawk(List<Credential> hawk) { this.hawk = hawk; }
    public List<Credential> getNtlm() { return ntlm; }
    public void setNtlm(List<Credential> ntlm) { this.ntlm = ntlm; }
    public List<Credential> getOauth1() { return oauth1; }
    public void setOauth1(List<Credential> oauth1) { this.oauth1 = oauth1; }
    public List<Credential> getOauth2() { return oauth2; }
    public void setOauth2(List<Credential> oauth2) { this.oauth2 = oauth2; }
}