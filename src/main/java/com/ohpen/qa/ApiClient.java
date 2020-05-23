package com.ohpen.qa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static org.springframework.test.util.AssertionErrors.assertNotNull;

@Component
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "url",
        "port",
        "user",
        "pass"
})
public class ApiClient {

    private static Logger log = LogManager.getLogger(ApiClient.class);

    @JsonProperty("url")
    private String url;
    @JsonProperty("port")
    private Integer port;
    @JsonProperty("user")
    private String user;
    @JsonProperty("pass")
    private String pass;

    int lastHttpCode;

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("port")
    public Integer getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @JsonProperty("user")
    public String getUser() {
        return user;
    }

    @JsonProperty("user")
    public void setUser(String user) {
        this.user = user;
    }

    @JsonProperty("pass")
    public String getPass() {
        return pass;
    }

    @JsonProperty("pass")
    public void setPass(String pass) {
        this.pass = pass;
    }

    protected ObjectMapper mapper;
    private RequestConfig config;
    protected CloseableHttpClient client;
    protected String executionEnv = "";
    protected String credentials;

    private Boolean needsProxy;
    private HttpHost host;
    private Integer connectTimeout = 60;
    private Integer socketTimeout = 60;
    private String keystore = "jssecacerts";
    private String password = "changeit";

    @PostConstruct
    public void init()
    {
        if (getUrl() != null) {
            log.info("Starting new API client for " + getUrl() + ":" + getPort());
            JsonFactory factory = new JsonFactory();
            mapper = new ObjectMapper(factory);

            mapper.setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            if (getUrl().contains("https:"))
            {
                executionEnv = getUrl() + (getPort() != 443 ? ":" + getPort() : "");
                try {
                    this.client = getSecureClient(url, port);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else
            {
                executionEnv = getUrl() + (getPort() != 80 ? ":" + getPort() : "");
                this.client = getClient();
            }

            if (getUser() != null && getPass() != null)
            {
                byte[] creds = Base64.encodeBase64((getUser() + ":" + getPass()).getBytes(StandardCharsets.UTF_8));
                credentials = new String(creds, StandardCharsets.UTF_8);
            }
            else credentials = null;
        }
    }

    public CloseableHttpClient getClient()
    {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        // this fixes the thread being stuck when 400 Bad Request is not sent in reasonable time
        if (needsProxy != null && needsProxy)
        {
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(host);
            config = RequestConfig
                    .custom()
                    .setConnectTimeout(connectTimeout * 1000)
                    .setSocketTimeout(socketTimeout * 1000)
                    .setConnectionRequestTimeout(connectTimeout * 1000)
                    .setProxy(host)
                    .build();

            return httpClientBuilder.setRoutePlanner(routePlanner).build();
        }
        else
        {
            log.info("Created unsecured connection handler for " + getUrl());
            config = RequestConfig
                    .custom()
                    .setConnectTimeout(connectTimeout * 1000)
                    .setSocketTimeout(socketTimeout * 1000)
                    .setConnectionRequestTimeout(connectTimeout * 1000)
                    .build();

            return httpClientBuilder.build();
        }
    }

    protected void buildHeader(HttpRequestBase x)
    {
        x.setConfig(getProxyable());  // allows DNS to be resolved via proxy
        x.addHeader("Accept", "application/json");
        x.addHeader("Content-Type", "application/json");
        x.addHeader("Access-Control-Allow-Origin", "*");
        x.addHeader("Connection", "keep-alive");
        x.addHeader("Cache-Control", "no-cache");
        x.addHeader("Pragma", "no-cache");
        if (credentials != null)
            x.addHeader("Authorization", "Basic " + credentials);
    }

    protected Optional<String> getBody(HttpEntity responseEntity) {
        StringBuilder sb = new StringBuilder();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseEntity.getContent()), 65279);
            String line;

            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }

        } catch (IOException e) {
            log.error(e);
            return Optional.empty();
        }
        return Optional.of(sb.toString());
    }

    private Optional<File> downloadBody(HttpEntity responseEntity, String filePath)
    {
        try {
            BufferedInputStream is = new BufferedInputStream(responseEntity.getContent());
            File result = new File(filePath);
            BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(result));
            org.apache.commons.io.IOUtils.copy(is, os);
            os.close();
            return Optional.of(result);
        }
        catch (FileNotFoundException e)
        {
            log.error("Failed to write to " + filePath);
        }
        catch(IOException e)
        {
            throw new RuntimeException("[QA] " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    protected HttpPost buildPostHeader(String url, String request) {
        HttpPost post = new HttpPost(url);
        buildHeader(post);
        post.setConfig(getProxyable());
        try {
            post.setEntity(new StringEntity(request));
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        }
        log.info(post.toString());
        return post;
    }

    protected HttpPut buildPutHeader(String url, String request) {
        HttpPut put = new HttpPut(url);
        buildHeader(put);
        put.setConfig(getProxyable());
        try {
            put.setEntity(new StringEntity(request));
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        }
        log.info(put.toString());
        return put;
    }

    protected HttpDelete buildDeleteHeader(String url) {
        HttpDelete delete = new HttpDelete(url);
        buildHeader(delete);
        delete.setConfig(getProxyable());
        log.info(delete.toString());
        return delete;
    }

    protected HttpGet buildGetHeader(String url) {
        url = url.replaceAll("//", "/").replaceAll(":/", "://");
        HttpGet get = new HttpGet(url);
        buildHeader(get);
        get.setConfig(getProxyable());
        log.info(get.toString());
        return get;
    }

    @Override
    public String toString() {
        return executionEnv;
    }

    protected Optional<String> execute(HttpRequestBase req, int expected)
    {
        CloseableHttpResponse response;
        Optional<String> result = Optional.empty();
        if (client == null)
        {
            log.error("Unable to request " + req.getRequestLine().getUri());
        }
        else try
        {
            response = client.execute(req);
            lastHttpCode = response.getStatusLine().getStatusCode();

            if ((lastHttpCode / 100) == (expected / 100))
            {
                log.info(response.getStatusLine());
                if (lastHttpCode != 204)
                {
                    result = getBody(response.getEntity());
                }
                response.close();
            }
            else {
                log.error(response.getStatusLine() + "; expected was " + expected / 100 + "XX");
                response.close();
            }
        }
        catch (javax.net.ssl.SSLHandshakeException msg)
        {
            String proto = System.getProperty("https.protocols");
            log.error("SSL handshake failed; keychain does not contain the correct certificate");
            throw new RuntimeException("[QA] SSL Handshake failed on " + executionEnv + " -> certificate is missing from job keychain");
        }
        catch (IOException msg)
        {
            log.error(msg);
        }
        if (req != null)
            req.releaseConnection();

        return result;
    }

    protected Optional<String> execute(HttpRequestBase req)
    {
        return execute(req, 200);
    }

    public RequestConfig getProxyable()
    {
        return config;
    }

    public void setNeedsProxy(Boolean enabled) {
        this.needsProxy = enabled;
    }

    public CloseableHttpClient getSecureClient(String url, int port) throws InterruptedException {
        File file = new File(keystore);
        if (!file.exists()) {
            char SEP = File.separatorChar;
            File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
            file = new File(dir, keystore);
            if (!file.isFile()) {
                file = new File(dir, "cacerts");
            }
        }

        int connectRetry = 0;

        do try
        {
            InputStream in = new FileInputStream(file);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(in, password.toCharArray());
            in.close();

            // check if stuff needs to be added to cacert
            SSLContext context = SSLContext.getInstance("TLS");

            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            final X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];

            final KeepAlive tm = new KeepAlive(defaultTrustManager);

            context.init(null, new TrustManager[]{tm}, new SecureRandom());


            SSLSocketFactory factory = context.getSocketFactory();

            log.info("Opening socket for " + URI.create(url).getHost() + ":" + port);
            SSLSocket socket = (SSLSocket) factory.createSocket(URI.create(url).getHost(), port);
            socket.setSoTimeout(socketTimeout * 1000);
            try {
                log.info("Starting SSL handshake...");
                socket.startHandshake();
                log.info("Successful " + socket.toString());
                socket.close();
                log.info("No errors, certificate is already trusted");
            } catch (SSLException e) {
                log.error(e.getMessage() + " on " + url + ":" + port);
            }

            X509Certificate[] chain = tm.chain;
            assertNotNull("[QA] Added self-signed certificate chain for " + url + " please restart the job", chain);

            log.info(url + " sent " + chain.length + " certificate(s):");
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                log.info("Certificate #" + (i + 1) + " Subject -> " + cert.getSubjectDN());
                log.info("Issuer -> " + cert.getIssuerDN());
                sha1.update(cert.getEncoded());
                //log.info("SHA1 Thumbprint -> " + toHexString(sha1.digest()));
            }

            for (int k = 0; k < chain.length; k++) {
                String alias = url + "-" + (k + 1);
                ks.setCertificateEntry(alias, chain[k]);
            }

            OutputStream out = new FileOutputStream(keystore);
            ks.store(out, password.toCharArray());
            out.close();

            HttpClientBuilder builder = HttpClients.custom()
                    .setSSLContext(context).setSSLHostnameVerifier(new NoopHostnameVerifier());

            return builder.build();
        }
        catch (Exception msg)
        {
            log.error(msg.getMessage());
        }
        finally {
            sleep(1000 * 5);
        }
        while (connectRetry++ < 2);
        return null;
    }

    protected String getItem(String urlPart)
    {
        HttpGet get = buildGetHeader(executionEnv + "/" + urlPart);
        Optional<String> result = execute(get);
        return result.orElse(null);
    }

    protected <T> T getItem(String urlPart, Class<T> t)
    {
        HttpGet get = buildGetHeader(executionEnv + "/" + urlPart);
        return execute(get).map(result -> {
            try {
                return mapper.readValue(result, t);
            }
            catch (IOException e) {
                log.warn(e);
                return null;
            }
            finally {
                get.releaseConnection();
            }
        }).orElse(null);
    }

    protected <T> Optional<T> getOptionalItem(String urlPart, Class<T> t)
    {
        HttpGet get = buildGetHeader(executionEnv + "/" + urlPart);
        return execute(get).flatMap(result -> {
            try {
                return Optional.of(mapper.readValue(result, t));
            } catch (IOException e) {
                log.error(e);
                return Optional.empty();
            }
            finally {
                get.releaseConnection();
            }
        });
    }

    protected <T> T getItem(String urlPart, String payload, Class<T> t)
    {
        HttpPost post = buildPostHeader(executionEnv + "/" + urlPart, payload);
        return execute(post).map(result -> {
            try {
                return mapper.readValue(result, t);
            } catch (IOException e) {
                log.error("Invalid item of type " + t.getName() + " -> " + payload);
                log.error(e);
                return null;
            }
            finally {
                post.releaseConnection();
            }
        }).orElse(null);
    }

    protected <T,K> T getItem(String urlPart, K payload, Class<T> t)
    {
        try {
            return getItem(urlPart, mapper.writeValueAsString(payload), t);
        } catch (JsonProcessingException e) {
            log.error(e);
            return null;
        }
    }

    protected <T> List<T>
    getList(String urlPart, Class<T> t)
    {
        HttpGet get = buildGetHeader(executionEnv + "/" + urlPart);
        Optional<String> request = execute(get);
        List<T> result = Collections.emptyList();
        if (request.isPresent())
        {
            try {
                result = mapper.readValue(request.get(), mapper.getTypeFactory().constructCollectionType(List.class, t));
            } catch (IOException e) {
                log.error(e);
            }
        }
        return result;
    }

    <T> List<T> getList(String urlPart, String payload, Class<T> t)
    {
        HttpPost post = buildPostHeader(executionEnv + "/" + urlPart, payload);
        Optional<String> request = execute(post);
        List<T> result = Collections.emptyList();
        if (request.isPresent())
        {
            try {
                result = mapper.readValue(request.get(), mapper.getTypeFactory().constructCollectionType(List.class, t));
            } catch (IOException e) {
                log.error(e);
            }
        }
        return result;
    }

    <T,K> List<T> getList(String urlPart, K payload, Class<T> t)
    {
        try {
            return getList(urlPart, mapper.writeValueAsString(payload), t);
        } catch (JsonProcessingException e) {
            log.error(e);
            return Collections.emptyList();
        }
    }


    <T,K> K setItem(String urlPart, T payload, Class<K> k)
    {
        Optional<String> result = setItem(urlPart, payload);
        if (result.isPresent())
        {
            try
            {
                return mapper.readValue(result.get(), k);
            }
            catch (Exception msg)
            {
                log.error(msg);
            }
        }
        return null;
    }
    public <T> Optional<String> setItem(String urlPart, T payload)
    {
        HttpPost post;
        try {
            post = buildPostHeader(executionEnv + "/" + urlPart, mapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error(e);
            return Optional.empty();
        }

        try {
            HttpResponse response = client.execute(post);
            lastHttpCode = response.getStatusLine().getStatusCode();
            if (lastHttpCode == 204)
            {
                return Optional.of("");
            }
            if (lastHttpCode / 100 == 2)
            {
                HttpEntity item = response.getEntity();
                return getBody(item);
            }
        }
        catch (IOException e) {
            // TODO: handle errors from here properly and throw AssertionError/OperationNotSupported
            log.error(e);
        }
        finally
        {
            post.releaseConnection();
        }
        return Optional.empty();
    }

    protected <T> Optional<String> putItem(String urlPart, T payload)
    {
        HttpPut put;
        try {
            put = buildPutHeader(executionEnv + "/" + urlPart, mapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error(e);
            return Optional.empty();
        }

        try {
            HttpResponse response = client.execute(put);
            lastHttpCode = response.getStatusLine().getStatusCode();
            if (lastHttpCode == 204)
            {
                return Optional.of("");
            }
            if (lastHttpCode / 100 == 2)
            {
                HttpEntity item = response.getEntity();
                return getBody(item);
            }
        }
        catch (IOException e) {
            log.error(e);
        }
        finally
        {
            put.releaseConnection();
        }
        return Optional.empty();
    }

    protected Optional<String> deleteItem(String urlPart)
    {
        HttpDelete delete = buildDeleteHeader(executionEnv + "/" + urlPart);

        try {
            HttpResponse response = client.execute(delete);
            lastHttpCode = response.getStatusLine().getStatusCode();
            if (lastHttpCode == 204)
            {
                return Optional.of("");
            }
            if (lastHttpCode / 100 == 2)
            {
                HttpEntity item = response.getEntity();
                return getBody(item);
            }
        }
        catch (IOException e) {
            log.error(e);
        }
        finally
        {
            delete.releaseConnection();
        }
        return Optional.empty();
    }


    boolean set(String urlPart, String payload)
    {
        HttpPost post = buildPostHeader(executionEnv + "/" + urlPart, payload);
        try {
            HttpResponse response = client.execute(post);
            lastHttpCode = response.getStatusLine().getStatusCode();
            return lastHttpCode == 204;
        }
        catch (IOException e) {
            log.error(e);
        }
        return false;
    }

    static class KeepAlive implements X509TrustManager
    {
        private final X509TrustManager tm;
        X509Certificate[] chain;
        private static final Logger log = LogManager.getLogger(KeepAlive.class);

        KeepAlive(X509TrustManager tm) {
            this.tm = tm;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
            log.warn("Reconnecting client SSL using " + s);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            log.warn("Reconnecting server SSL using " + s);
            this.chain = x509Certificates;
            tm.checkServerTrusted(chain, s);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            // Bypasses system-accepted certificate issuers
            return null;
        }
    }

}
