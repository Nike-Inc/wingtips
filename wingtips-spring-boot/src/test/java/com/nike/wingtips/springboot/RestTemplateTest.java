package com.nike.wingtips.springboot;

import com.nike.wingtips.Tracer;
import com.nike.wingtips.springboot.support.Greeting;
import com.nike.wingtips.springboot.support.GreetingApplication;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {GreetingApplication.class})
public class RestTemplateTest {
	// This will hold the port number the server was started on
	@Value("${local.server.port}")
	int port;

	final RestTemplate template = Tracing.createRestTemplate();

	@Before
	public void before() {
		Tracer.getInstance().startRequestWithRootSpan("SomeSpan1");
	}

	@After
	public void after() {
		Tracer.getInstance().completeRequestSpan();
	}

	@Test
	public void callServiceTest() {
		Greeting message = template.getForObject("http://localhost:" + port + "/greeting", Greeting.class);
		Assert.assertEquals("Hello, World!", message.getContent());
		Assert.assertEquals(1, message.getId());
	}
}
