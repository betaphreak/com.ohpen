package com.ohpen.qa.test;

import com.ohpen.qa.github.GitHubClient;
import com.ohpen.qa.github.UserDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;

@ContextConfiguration(locations = "/applicationContext.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class GitHubTest
{
    private static Logger log = LogManager.getLogger(GitHubTest.class);

    @Resource(name = "github")
    private GitHubClient client;

    @Test
    public void GetUserTest()
    {
        client.setCredentials(null);
        UserDTO user = client.getUser("betaphreak");
        log.info(user.toString());
    }

}
