package io.vintra.qa.server;

import io.vintra.qa.ApiClient;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class TestClient extends ApiClient
{
    private static Logger log = LogManager.getLogger(TestClient.class);

    @Nullable
    public void setCredentials(String encoded)
    {
        credentials = encoded;
    }

    public LoginResponseDTO login(String user, String pass) {
        HttpPost post = null;
        try {
            post = buildPostHeader( executionEnv + "/auth/login", "");
            List<NameValuePair> form = new ArrayList<>();
            form.add(new BasicNameValuePair("username", user));
            form.add(new BasicNameValuePair("password", pass));
            post.setEntity(new UrlEncodedFormEntity(form));
            Header content = post.getFirstHeader("Content-Type");
            post.removeHeader(content);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
        } catch (UnsupportedEncodingException msg) {

        }

        HttpPost finalPost = post;
        return execute(post).map(result -> {
            try {
                return mapper.readValue(result, LoginResponseDTO.class);
            } catch (IOException e) {
                log.error("Invalid auth ", e);
                return null;
            } finally {
                finalPost.releaseConnection();
            }
        }).orElse(null);
    }

    public UserDTO getCurrentUser()
    {
        return getItem("/auth/me", UserDTO.class);
    }

}
