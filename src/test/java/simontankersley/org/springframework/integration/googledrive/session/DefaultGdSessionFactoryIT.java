package nhethast.org.springframework.integration.googledrive.session;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.core.io.FileSystemResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import com.google.api.services.drive.DriveScopes;

public class DefaultGdSessionFactoryIT {

	private DefaultGdSessionFactory factory;
	
	@BeforeClass
	public static void beforeClass() {
		// as per http://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
		// call this to send java util logging calls to slf4j
		SLF4JBridgeHandler.install();
	}
	
	@Before
	public void before() {
		MessageChannel authUrlChannel = new MessageChannel() {
			
			@Override
			public boolean send(Message<?> message, long timeout) {
				System.out.println(message.getPayload());
				return false;
			}
			
			@Override
			public boolean send(Message<?> message) {
				System.out.println(message.getPayload());
				return true;
			}
		};
		
		factory = new DefaultGdSessionFactory();
		factory.setAuthFlowTimeout(30000);
		factory.setAuthTimeout(10000);
		factory.setAuthUrlChannel(authUrlChannel);
		factory.setClientSecretsJson(new FileSystemResource("client_secrets.json"));
		factory.setDataStoreDirectory(new FileSystemResource("."));
		factory.setEmail("ticheryonschardogric@gmail.com");
		factory.setHost("localhost");
		factory.setPath("aewfaewfawefaefaewfawefaw");
		factory.setPort(8080);
		factory.setUseTitleAndId(true);
	}
	
	@Test
	public void testExists() throws IOException {
		System.out.println(factory.getSession().exists("/New Folder/test"));
	}
	
	@Test
	public void testMkdir() throws IOException {
		System.out.println(factory.getSession().mkdir("test dir"));
	}
	
	@Test
	public void testRemove() throws IOException {
		System.out.println(factory.getSession().remove("test dir/test"));
	}
	
	@Test
	public void testList() throws IOException {
		System.out.println(Arrays.toString(factory.getSession().list("")));
	}
	
	@Test
	public void testListNames() throws IOException {
		System.out.println(Arrays.toString(factory.getSession().listNames("")));
		System.out.println(Arrays.toString(factory.getSession().listNames("/")));
	}
	
	@Test
	public void testReadRaw() throws IOException {
		System.out.println(IOUtils.toString(factory.getSession().readRaw("README.md")));
	}
	
	@Test
	public void testRename() throws IOException {
		factory.getSession().rename("README.md","README2.md");
	}
	
	@Test
	public void testWrite() throws IOException {
		factory.getSession().write(IOUtils.toInputStream("testing testing 123"), "README.md");
	}
	
}
