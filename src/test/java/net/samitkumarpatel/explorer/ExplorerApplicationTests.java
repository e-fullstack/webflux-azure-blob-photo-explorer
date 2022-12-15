package net.samitkumarpatel.explorer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ExplorerApplicationTests {

	@Test
	void contextLoads() {
	}

}

@ExtendWith(SpringExtension.class)
class UtilityTest {

	@Test
	void specialCharacterReplacementTest() {
		var x = "India tour_2020".replaceAll("\\W", "-").replaceAll("_", "-");
		assertEquals("India-tour-2020", x);
	}
}