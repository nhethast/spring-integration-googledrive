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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.synchronizer.AbstractInboundFileSynchronizer;
import nhethast.org.springframework.integration.googledrive.filters.GdSimplePatternFileListFilter;
import nhethast.org.springframework.integration.googledrive.inbound.GdInboundFileSynchronizer;
import nhethast.org.springframework.integration.googledrive.inbound.GdInboundFileSynchronizingMessageSource;
import nhethast.org.springframework.integration.googledrive.session.DefaultGdSessionFactory;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;

/**
 * 
 * 
 * @author Oleg Zhurakousky
 * @author Mark Fisher
 * @author Gary Russell
 * @author Gunnar Hillert
 */
public class GdInboundChannelAdapterParserTests {

	@Test
	public void testGdInboundChannelAdapterComplete() throws Exception{
		ApplicationContext ac =
			new ClassPathXmlApplicationContext("GdInboundChannelAdapterParserTests-context.xml", this.getClass());
		SourcePollingChannelAdapter adapter = ac.getBean("gdInbound", SourcePollingChannelAdapter.class);
		assertFalse(TestUtils.getPropertyValue(adapter, "autoStartup", Boolean.class));
		PriorityBlockingQueue<?> blockingQueue = TestUtils.getPropertyValue(adapter, "source.fileSource.toBeReceived", PriorityBlockingQueue.class);
		Comparator<?> comparator = blockingQueue.comparator();
		assertNotNull(comparator);
		assertEquals("gdInbound", adapter.getComponentName());
		assertEquals("gd:inbound-channel-adapter", adapter.getComponentType());
		assertNotNull(TestUtils.getPropertyValue(adapter, "poller"));
		assertEquals(ac.getBean("gdChannel"), TestUtils.getPropertyValue(adapter, "outputChannel"));
		GdInboundFileSynchronizingMessageSource inbound =
			(GdInboundFileSynchronizingMessageSource) TestUtils.getPropertyValue(adapter, "source");

		GdInboundFileSynchronizer fisync =
			(GdInboundFileSynchronizer) TestUtils.getPropertyValue(inbound, "synchronizer");
		assertNotNull(TestUtils.getPropertyValue(fisync, "localFilenameGeneratorExpression"));
		assertTrue(TestUtils.getPropertyValue(fisync, "preserveTimestamp", Boolean.class));
		assertEquals(".foo", TestUtils.getPropertyValue(fisync, "temporaryFileSuffix", String.class));
		String remoteFileSeparator = (String) TestUtils.getPropertyValue(fisync, "remoteFileSeparator");
		assertNotNull(remoteFileSeparator);
		assertEquals("", remoteFileSeparator);
		GdSimplePatternFileListFilter filter = (GdSimplePatternFileListFilter) TestUtils.getPropertyValue(fisync, "filter");
		assertNotNull(filter);
		Object sessionFactory = TestUtils.getPropertyValue(fisync, "remoteFileTemplate.sessionFactory");
		assertTrue(DefaultGdSessionFactory.class.isAssignableFrom(sessionFactory.getClass()));
		FileListFilter<?> acceptAllFilter = ac.getBean("acceptAllFilter", FileListFilter.class);
		assertTrue(TestUtils.getPropertyValue(inbound, "fileSource.scanner.filter.fileFilters", Collection.class).contains(acceptAllFilter));
		final AtomicReference<Method> genMethod = new AtomicReference<Method>();
		ReflectionUtils.doWithMethods(AbstractInboundFileSynchronizer.class, new MethodCallback() {

			@Override
			public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
				if ("generateLocalFileName".equals(method.getName())) {
					method.setAccessible(true);
					genMethod.set(method);
				}
			}
		});
		assertEquals("FOO.afoo", genMethod.get().invoke(fisync, "foo"));
	}

	@Test
	public void cachingSessionFactory() throws Exception{
		ApplicationContext ac = new ClassPathXmlApplicationContext(
				"GdInboundChannelAdapterParserTests-context.xml", this.getClass());
		SourcePollingChannelAdapter adapter = ac.getBean("simpleAdapterWithCachedSessions", SourcePollingChannelAdapter.class);
		Object sessionFactory = TestUtils.getPropertyValue(adapter, "source.synchronizer.remoteFileTemplate.sessionFactory");
		assertEquals(CachingSessionFactory.class, sessionFactory.getClass());
		GdInboundFileSynchronizer fisync =
			TestUtils.getPropertyValue(adapter, "source.synchronizer", GdInboundFileSynchronizer.class);
		String remoteFileSeparator = (String) TestUtils.getPropertyValue(fisync, "remoteFileSeparator");
		assertNotNull(remoteFileSeparator);
		assertEquals("/", remoteFileSeparator);
	}

	@Test
	public void testGdInboundChannelAdapterCompleteNoId() throws Exception{
		ApplicationContext ac =
			new ClassPathXmlApplicationContext("GdInboundChannelAdapterParserTests-context.xml", this.getClass());
		Map<String, SourcePollingChannelAdapter> spcas = ac.getBeansOfType(SourcePollingChannelAdapter.class);
		SourcePollingChannelAdapter adapter = null;
		for (String key : spcas.keySet()) {
			if (!key.equals("gdInbound") && !key.equals("simpleAdapter")){
				adapter = spcas.get(key);
			}
		}
		assertNotNull(adapter);
	}

	@Test
	public void testAutoChannel() {
		ApplicationContext context =
			new ClassPathXmlApplicationContext("GdInboundChannelAdapterParserTests-context.xml", this.getClass());
		// Auto-created channel
		MessageChannel autoChannel = context.getBean("autoChannel", MessageChannel.class);
		SourcePollingChannelAdapter autoChannelAdapter = context.getBean("autoChannel.adapter", SourcePollingChannelAdapter.class);
		assertSame(autoChannel, TestUtils.getPropertyValue(autoChannelAdapter, "outputChannel"));
	}

	public static class TestSessionFactoryBean implements FactoryBean<DefaultGdSessionFactory> {

		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public DefaultGdSessionFactory getObject() throws Exception {
			DefaultGdSessionFactory factory = mock(DefaultGdSessionFactory.class);
			Session session = mock(Session.class);
			when(factory.getSession()).thenReturn(session);
			return factory;
		}

		@Override
		public Class<?> getObjectType() {
			return DefaultGdSessionFactory.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}
	}

}
