package nhethast.org.springframework.integration.googledrive;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:InboundGdTest-context.xml"})
public class InboundGdTest {

	@Test
	public void test() {
		System.out.println("blah");
	}
	
}
