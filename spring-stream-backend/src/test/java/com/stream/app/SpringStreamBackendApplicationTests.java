package com.stream.app;

import com.stream.app.services.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringStreamBackendApplicationTests {

	@Autowired
	private VideoService videoService;
	@Test
	void contextLoads() {
		videoService.processVideo("e0314b9b-b048-4b42-b8ba-148eb89da1bf");
	}




}
