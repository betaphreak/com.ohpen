package io.vintra.qa.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.codearte.jfairy.Fairy;
import io.vintra.qa.ApiClient;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.lang.Nullable;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.util.AssertionErrors.assertEquals;

public class TestClient extends ApiClient
{
    private static Logger log = LogManager.getLogger(TestClient.class);
    private Fairy fairy;

    @PostConstruct
    public void init()
    {
        super.init();
        fairy = Fairy.create();
    }


    @Nullable
    public void setCredentials(String encoded)
    {
        credentials = encoded;
    }

    @Nullable
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
        UserDTO user = getItem("/auth/me", UserDTO.class);
        assertEquals("Get authenticated user failed", getLastHttpCode(), 200);
        return user;
    }

    public void logout()
    {
        getItem("/auth/logout");
        assertEquals("Logout failed", getLastHttpCode(), 200);
        setCredentials(null);
    }

    public ContactDTO createContact(ContactFairy contact)
    {
        Optional<String> result = setItem("api/v1/contacts", contact);
        return result.map(response -> {
            if (getLastHttpCode() == 201) {
                try {
                    return mapper.readValue(response, ContactDTO.class);
                } catch (JsonProcessingException e) {
                    log.warn(e);
                }
            }
            else log.error(response);
            return null;
        }).orElse(null);
    }

    public ContactDTO createContact()
    {
        ContactFairy contact = new ContactFairy(fairy);
        return createContact(contact);
    }


}
