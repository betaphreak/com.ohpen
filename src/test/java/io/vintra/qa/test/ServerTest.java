package io.vintra.qa.test;

import io.vintra.qa.server.LoginResponseDTO;
import io.vintra.qa.server.TestClient;
import io.vintra.qa.server.UserDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;

import static org.junit.Assert.assertNotNull;

@ContextConfiguration(locations = "/applicationContext.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ServerTest {
    private static Logger log = LogManager.getLogger(ServerTest.class);

    @Resource(name = "client")
    private TestClient client;

    @Before
    public void auth01()
    {
        LoginResponseDTO login = client.login("test", "1234");
        client.setCredentials(login.getTokenType() + " " + login.getAccessToken());
    }

    @Test
    public void auth02Test() {
        UserDTO user = client.getCurrentUser();
        log.info(user.toString());
    }
}