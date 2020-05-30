package io.vintra.qa.server;

import io.vintra.qa.ApiClient;
import org.springframework.lang.Nullable;

import static org.springframework.web.util.UriUtils.encode;

public class TestClient extends ApiClient
{
    @Nullable
    public void setCredentials(String encoded)
    {
        credentials = encoded;
    }

    @Nullable
    public UserDTO getUser(String userName)
    {
        return getItem("users/" + encode(userName, "UTF-8"), UserDTO.class);
    }

}
