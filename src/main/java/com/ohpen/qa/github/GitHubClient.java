package com.ohpen.qa.github;

import com.ohpen.qa.ApiClient;
import org.springframework.lang.Nullable;

import static org.springframework.web.util.UriUtils.encode;

public class GitHubClient extends ApiClient
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
