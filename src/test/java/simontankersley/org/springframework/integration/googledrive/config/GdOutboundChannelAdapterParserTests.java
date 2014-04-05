/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nhethast.org.springframework.integration.googledrive.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Set;

import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.file.DefaultFileNameGenerator;
import org.springframework.integration.file.remote.handler.FileTransferringMessageHandler;
import org.springframework.integration.file.remote.session.CachingSessionFactory;

import nhethast.org.springframework.integration.googledrive.session.DefaultGdSessionFactory;
import nhethast.org.springframework.integration.googledrive.session.GdSessionFactory;

import org.springframework.integration.handler.ExpressionEvaluatingMessageProcessor;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;

/**
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Gunnar Hillert
 * @since 2.0
 */
public class GdOutboundChannelAdapterParserTests {

	private static volatile int adviceCalled;

	@Test
	public void testGdOutboundChannelAdapterComplete() throws Exception{
		ApplicationContext ac =
			new ClassPathXmlApplicationContext("GdOutboundChannelAdapterParserTests-context.xml", this.getClass());
		Object consumer = ac.getBean("ftpOutbound");
		assertTrue(consumer instanceof EventDrivenConsumer);
		PublishSubscribeChannel channel = ac.getBean("ftpChannel", PublishSubscribeChannel.class);
		assertEquals(channel, TestUtils.getPropertyValue(consumer, "inputChannel"));
		assertEquals("ftpOutbound", ((EventDrivenConsumer)consumer).getComponentName());
		FileTransferringMessageHandler<?> handler = TestUtils.getPropertyValue(consumer, "handler", FileTransferringMessageHandler.class);
		String remoteFileSeparator = (String) TestUtils.getPropertyValue(handler, "remoteFileTemplate.remoteFileSeparator");
		assertNotNull(remoteFileSeparator);
		assertEquals(".foo", TestUtils.getPropertyValue(handler, "remoteFileTemplate.temporaryFileSuffix", String.class));
		assertEquals("", remoteFileSeparator);
		assertEquals(ac.getBean("fileNameGenerator"), TestUtils.getPropertyValue(handler, "remoteFileTemplate.fileNameGenerator"));
		assertEquals("UTF-8", TestUtils.getPropertyValue(handler, "remoteFileTemplate.charset"));
		assertNotNull(TestUtils.getPropertyValue(handler, "remoteFileTemplate.directoryExpressionProcessor"));
		assertNotNull(TestUtils.getPropertyValue(handler, "remoteFileTemplate.temporaryDirectoryExpressionProcessor"));
		Object sfProperty = TestUtils.getPropertyValue(handler, "remoteFileTemplate.sessionFactory");
		assertEquals(DefaultGdSessionFactory.class, sfProperty.getClass());
		GdSessionFactory sessionFactory = (GdSessionFactory) sfProperty;
		assertEquals("localhost", TestUtils.getPropertyValue(sessionFactory, "host"));
		assertEquals(22, TestUtils.getPropertyValue(sessionFactory, "port"));
		assertEquals(23, TestUtils.getPropertyValue(handler, "order"));
		//verify subscription order
		@SuppressWarnings("unchecked")
		Set<MessageHandler> handlers = (Set<MessageHandler>) TestUtils
				.getPropertyValue(
						TestUtils.getPropertyValue(channel, "dispatcher"),
						"handlers");
		Iterator<MessageHandler> iterator = handlers.iterator();
		assertSame(TestUtils.getPropertyValue(ac.getBean("ftpOutbound2"), "handler"), iterator.next());
		assertSame(handler, iterator.next());
	}

	@Test(expected=BeanCreationException.class)
	public void testFailWithEmptyRfsAndAcdTrue() throws Exception{
		new ClassPathXmlApplicationContext("GdOutboundChannelAdapterParserTests-fail.xml", this.getClass());
	}

	@Test
	public void cachingByDefault() {
		ApplicationContext ac = new ClassPathXmlApplicationContext(
				"GdOutboundChannelAdapterParserTests-context.xml", this.getClass());
		Object adapter = ac.getBean("simpleAdapter");
		Object sfProperty = TestUtils.getPropertyValue(adapter, "handler.remoteFileTemplate.sessionFactory");
		assertEquals(CachingSessionFactory.class, sfProperty.getClass());
		Object innerSfProperty = TestUtils.getPropertyValue(sfProperty, "sessionFactory");
		assertEquals(DefaultGdSessionFactory.class, innerSfProperty.getClass());
	}

	@Test
	public void adviceChain() {
		ApplicationContext ac = new ClassPathXmlApplicationContext(
				"GdOutboundChannelAdapterParserTests-context.xml", this.getClass());
		Object adapter = ac.getBean("advisedAdapter");
		MessageHandler handler = TestUtils.getPropertyValue(adapter, "handler", MessageHandler.class);
		handler.handleMessage(new GenericMessage<String>("foo"));
		assertEquals(1, adviceCalled);
	}

	@Test
	public void testTemporaryFileSuffix() {
		ApplicationContext ac =
				new ClassPathXmlApplicationContext("GdOutboundChannelAdapterParserTests-context.xml", this.getClass());
			FileTransferringMessageHandler<?> handler =
					(FileTransferringMessageHandler<?>)TestUtils.getPropertyValue(ac.getBean("ftpOutbound3"), "handler");
			assertFalse((Boolean)TestUtils.getPropertyValue(handler,"remoteFileTemplate.useTemporaryFileName"));
	}

	@Test
	public void testBeanExpressions() throws Exception{
		ApplicationContext ac =
			new ClassPathXmlApplicationContext("GdOutboundChannelAdapterParserTests-context.xml", this.getClass());
		Object consumer = ac.getBean("withBeanExpressions");
		FileTransferringMessageHandler<?> handler = TestUtils.getPropertyValue(consumer, "handler", FileTransferringMessageHandler.class);
		ExpressionEvaluatingMessageProcessor<?> dirExpProc = TestUtils.getPropertyValue(handler,
				"remoteFileTemplate.directoryExpressionProcessor", ExpressionEvaluatingMessageProcessor.class);
		assertNotNull(dirExpProc);
		Message<String> message = MessageBuilder.withPayload("qux").build();
		assertEquals("foo", dirExpProc.processMessage(message));
		ExpressionEvaluatingMessageProcessor<?> tempDirExpProc = TestUtils.getPropertyValue(handler,
				"remoteFileTemplate.temporaryDirectoryExpressionProcessor", ExpressionEvaluatingMessageProcessor.class);
		assertNotNull(tempDirExpProc);
		assertEquals("bar", tempDirExpProc.processMessage(message));
		DefaultFileNameGenerator generator = TestUtils.getPropertyValue(handler,
				"remoteFileTemplate.fileNameGenerator", DefaultFileNameGenerator.class);
		assertNotNull(generator);
		assertEquals("baz", generator.generateFileName(message));
	}

	public static class FooAdvice extends AbstractRequestHandlerAdvice {

		@Override
		protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) throws Exception {
			adviceCalled++;
			return null;
		}

	}
}
